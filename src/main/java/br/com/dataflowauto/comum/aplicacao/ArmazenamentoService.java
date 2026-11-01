package br.com.dataflowauto.comum.aplicacao;

import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.comum.infra.ArquivoImportadoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Valida e guarda os arquivos enviados fora da pasta pública da aplicação.
 * O nome no disco é um UUID, então o nome original nunca vira caminho de arquivo.
 */
@Service
public class ArmazenamentoService {

    private final Path diretorio;
    private final long tamanhoMaximoBytes;
    private final ArquivoImportadoRepository repositorio;

    public ArmazenamentoService(@Value("${dataflow.armazenamento.diretorio}") String diretorio,
                                @Value("${dataflow.armazenamento.tamanho-maximo-mb}") long tamanhoMaximoMb,
                                ArquivoImportadoRepository repositorio) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        this.tamanhoMaximoBytes = tamanhoMaximoMb * 1024 * 1024;
        this.repositorio = repositorio;
    }

    @Transactional
    public ArquivoImportado guardar(MultipartFile arquivo, Set<TipoArquivo> tiposAceitos) {
        TipoArquivo tipo = validar(arquivo, tiposAceitos);
        try {
            Files.createDirectories(diretorio);
            String nomeNoDisco = UUID.randomUUID() + "." + tipo.getExtensao();
            Path destino = diretorio.resolve(nomeNoDisco);
            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino);
            }
            return repositorio.save(new ArquivoImportado(
                    nomeSeguro(arquivo.getOriginalFilename()), tipo, arquivo.getSize(), nomeNoDisco));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar o arquivo.", e);
        }
    }

    public TipoArquivo validar(MultipartFile arquivo, Set<TipoArquivo> tiposAceitos) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Nenhum arquivo foi enviado ou o arquivo está vazio.");
        }
        if (arquivo.getSize() > tamanhoMaximoBytes) {
            throw new ArquivoInvalidoException("O arquivo \"" + arquivo.getOriginalFilename()
                    + "\" passa do limite de " + (tamanhoMaximoBytes / (1024 * 1024)) + " MB.");
        }
        TipoArquivo tipo = TipoArquivo.porNomeArquivo(arquivo.getOriginalFilename())
                .filter(tiposAceitos::contains)
                .orElseThrow(() -> new ArquivoInvalidoException("Formato não aceito: \""
                        + arquivo.getOriginalFilename() + "\". Envie um arquivo " + listar(tiposAceitos) + "."));
        verificarAssinatura(arquivo, tipo);
        return tipo;
    }

    @Transactional(readOnly = true)
    public ArquivoImportado buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo " + id + " não encontrado."));
    }

    public InputStream abrir(ArquivoImportado arquivo) {
        Path caminho = diretorio.resolve(arquivo.getCaminhoArmazenamento()).normalize();
        if (!caminho.startsWith(diretorio)) {
            throw new ArquivoInvalidoException("Caminho de arquivo inválido.");
        }
        try {
            return Files.newInputStream(caminho);
        } catch (IOException e) {
            throw new RecursoNaoEncontradoException("O conteúdo do arquivo \"" + arquivo.getNomeOriginal()
                    + "\" não está mais disponível.");
        }
    }

    /** Confere os primeiros bytes para evitar, por exemplo, um executável renomeado para .xlsx. */
    private void verificarAssinatura(MultipartFile arquivo, TipoArquivo tipo) {
        byte[] cabecalho = new byte[4];
        int lidos;
        try (InputStream in = arquivo.getInputStream()) {
            lidos = in.readNBytes(cabecalho, 0, cabecalho.length);
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Não foi possível ler o arquivo enviado.", e);
        }
        boolean valido = switch (tipo) {
            case XLSX -> lidos == 4 && cabecalho[0] == 'P' && cabecalho[1] == 'K';
            case PDF -> lidos == 4 && cabecalho[0] == '%' && cabecalho[1] == 'P' && cabecalho[2] == 'D' && cabecalho[3] == 'F';
            case CSV, TXT -> lidos > 0 && !(cabecalho[0] == 'M' && lidos > 1 && cabecalho[1] == 'Z');
        };
        if (!valido) {
            throw new ArquivoInvalidoException("O conteúdo de \"" + arquivo.getOriginalFilename()
                    + "\" não corresponde a um arquivo " + tipo.getExtensao().toUpperCase() + " válido.");
        }
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "arquivo";
        }
        String base = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String listar(Set<TipoArquivo> tipos) {
        return String.join(" ou ", tipos.stream().map(t -> t.getExtensao().toUpperCase()).sorted().toList());
    }
}
