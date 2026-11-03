package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.fichas.dominio.CampoFicha;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.GrupoCampo;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.fichas.dominio.StatusVerificacao;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Gera a ficha técnica em PDF. Valores não confirmados aparecem marcados como
 * "a confirmar", e o PDF de uma ficha em revisão traz o aviso de rascunho.
 */
@Component
public class GeradorFichaPdf {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color COR_TITULO = new Color(0x1F, 0x3A, 0x5F);
    private static final Color COR_FUNDO_GRUPO = new Color(0xE8, 0xEE, 0xF5);
    private static final Color COR_PENDENTE = new Color(0xB4, 0x53, 0x09);

    public void gerar(FichaTecnica ficha, OutputStream destino, boolean publico) {
        Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(documento, destino);
            // no PDF público a placa não aparece inteira (nem nos metadados): só o final, como na página da loja
            String identificacaoPlaca = publico
                    ? "Final da placa " + ficha.getVeiculo().getPlaca().finalDaPlaca()
                    : "Placa " + ficha.getVeiculo().getPlacaFormatada();
            documento.addTitle(publico
                    ? "Ficha técnica " + ficha.getVeiculo().getDescricao()
                    : "Ficha técnica " + ficha.getVeiculo().getPlacaFormatada());
            documento.addCreator("Imperium Motors");
            documento.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COR_TITULO);
            Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            Font aviso = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COR_PENDENTE);

            documento.add(new Paragraph("Ficha Técnica", titulo));
            documento.add(new Paragraph(ficha.getVeiculo().getDescricao() + "  ·  " + identificacaoPlaca, subtitulo));
            documento.add(new Paragraph("Gerada em " + LocalDateTime.now().format(DATA_HORA)
                    + "  ·  Situação: " + ficha.getStatus().getDescricao(), subtitulo));
            if (ficha.getStatus() != StatusFicha.CONCLUIDA) {
                Paragraph p = new Paragraph("RASCUNHO: esta ficha contém informações ainda não confirmadas.", aviso);
                p.setSpacingBefore(6);
                documento.add(p);
            }
            documento.add(Chunk.NEWLINE);

            for (Map.Entry<GrupoCampo, List<CampoFicha>> grupo : ficha.camposPorGrupo().entrySet()) {
                List<CampoFicha> campos = publico
                        ? grupo.getValue().stream().filter(c -> c.getCampo().isPublico()).toList()
                        : grupo.getValue();
                if (!campos.isEmpty()) {
                    documento.add(tabelaGrupo(grupo.getKey(), campos));
                }
            }

            Font rodape = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
            // a lista de fontes traz nomes de arquivos enviados (ex.: o CRLV), então só vai no PDF interno
            String fontes = publico
                    ? ""
                    : " Fontes consultadas: " + (ficha.getFontesConsultadas() == null ? "—" : ficha.getFontesConsultadas()) + ".";
            Paragraph nota = new Paragraph("Valores marcados como \"a confirmar\" vieram de documento ou fonte externa e "
                    + "ainda não foram verificados." + fontes, rodape);
            nota.setSpacingBefore(10);
            documento.add(nota);
        } catch (DocumentException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF da ficha.", e);
        } finally {
            documento.close();
        }
    }

    private PdfPTable tabelaGrupo(GrupoCampo grupo, List<CampoFicha> campos) {
        PdfPTable tabela = new PdfPTable(new float[]{35, 65});
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(8);

        PdfPCell cabecalho = new PdfPCell(new Phrase(grupo.getDescricao(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COR_TITULO)));
        cabecalho.setColspan(2);
        cabecalho.setBackgroundColor(COR_FUNDO_GRUPO);
        cabecalho.setPadding(5);
        tabela.addCell(cabecalho);

        Font rotulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
        Font valor = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font pendente = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, COR_PENDENTE);
        Font ausente = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

        for (CampoFicha campo : campos) {
            tabela.addCell(celula(new Phrase(campo.getCampo().getRotulo(), rotulo)));
            Phrase conteudo;
            if (campo.getStatus() == StatusVerificacao.AUSENTE) {
                conteudo = new Phrase("—", ausente);
            } else if (campo.getStatus() == StatusVerificacao.A_CONFIRMAR) {
                conteudo = new Phrase(campo.getValor() + "  (a confirmar)", pendente);
            } else {
                conteudo = new Phrase(campo.getValor(), valor);
            }
            tabela.addCell(celula(conteudo));
        }
        return tabela;
    }

    private PdfPCell celula(Phrase conteudo) {
        PdfPCell cell = new PdfPCell(conteudo);
        cell.setPadding(4);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }
}
