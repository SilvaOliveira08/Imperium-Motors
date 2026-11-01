package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Obtém o texto de um documento enviado. PDFs com texto são lidos pelo PDFBox;
 * PDFs digitalizados (só imagem) precisariam de OCR, previsto como evolução.
 */
@Component
public class ExtratorTextoDocumento {

    public String extrair(byte[] conteudo, TipoArquivo tipo) {
        return switch (tipo) {
            case PDF -> extrairPdf(conteudo);
            case TXT -> new String(conteudo, StandardCharsets.UTF_8);
            default -> throw new ArquivoInvalidoException("Envie o documento em PDF ou TXT.");
        };
    }

    private String extrairPdf(byte[] conteudo) {
        try (PDDocument documento = Loader.loadPDF(conteudo)) {
            if (documento.isEncrypted()) {
                throw new ArquivoInvalidoException("O PDF está protegido por senha.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String texto = stripper.getText(documento);
            if (texto.isBlank()) {
                throw new ArquivoInvalidoException("O PDF não tem texto selecionável (parece ser uma imagem digitalizada). "
                        + "A leitura por OCR ainda não está disponível; preencha os dados manualmente.");
            }
            return texto;
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Não foi possível ler o PDF enviado.", e);
        }
    }
}
