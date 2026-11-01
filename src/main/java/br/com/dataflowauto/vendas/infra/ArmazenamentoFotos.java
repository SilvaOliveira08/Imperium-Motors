package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Guarda as fotos dos anúncios fora da pasta pública. O nome no disco é um UUID e o
 * conteúdo é conferido pela assinatura do arquivo, não só pela extensão.
 */
@Component
public class ArmazenamentoFotos {

    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    private static final String PADRAO_NOME = "[0-9a-f\\-]{36}\\.(jpg|png|webp)";

    private final Path diretorio;

    public ArmazenamentoFotos(@Value("${dataflow.fotos.diretorio:./dados/fotos}") String diretorio) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new ArquivoInvalidoException("Nenhuma foto foi enviada.");
        }
        if (foto.getSize() > TAMANHO_MAXIMO) {
            throw new ArquivoInvalidoException("A foto \"" + foto.getOriginalFilename() + "\" passa de 5 MB.");
        }
        try {
            byte[] bytes = foto.getBytes();
            String extensao = extensaoPorAssinatura(bytes);
            if (extensao == null) {
                throw new ArquivoInvalidoException("\"" + foto.getOriginalFilename() + "\" não é uma imagem JPG, PNG ou WEBP.");
            }
            Files.createDirectories(diretorio);
            String nome = UUID.randomUUID() + "." + extensao;
            Files.write(diretorio.resolve(nome), bytes);
            return nome;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar a foto.", e);
        }
    }

    public InputStream abrir(String nome) {
        Path caminho = resolver(nome);
        try {
            return Files.newInputStream(caminho);
        } catch (IOException e) {
            throw new RecursoNaoEncontradoException("Foto não encontrada.");
        }
    }

    public String tipoConteudo(String nome) {
        String ext = nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public void excluir(String nome) {
        try {
            Files.deleteIfExists(resolver(nome));
        } catch (IOException e) {
            // a referência já saiu do anúncio; um arquivo órfão não atrapalha o uso
        }
    }

    private Path resolver(String nome) {
        if (nome == null || !nome.matches(PADRAO_NOME)) {
            throw new RecursoNaoEncontradoException("Foto não encontrada.");
        }
        return diretorio.resolve(nome);
    }

    static String extensaoPorAssinatura(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
