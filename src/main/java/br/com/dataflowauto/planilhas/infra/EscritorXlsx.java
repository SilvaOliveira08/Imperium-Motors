package br.com.dataflowauto.planilhas.infra;

import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.planilhas.dominio.EscritorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Grava a planilha em .xlsx com cabeçalho em negrito e primeira linha congelada. */
@Component
public class EscritorXlsx implements EscritorPlanilha {

    private static final int MAX_LARGURA = 60 * 256;

    @Override
    public TipoArquivo formato() {
        return TipoArquivo.XLSX;
    }

    @Override
    public String tipoConteudo() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public void escrever(Planilha planilha, OutputStream destino) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {
            Sheet aba = workbook.createSheet(nomeAba(planilha.getNome()));
            ((SXSSFSheet) aba).trackAllColumnsForAutoSizing();

            CellStyle estiloCabecalho = workbook.createCellStyle();
            Font negrito = workbook.createFont();
            negrito.setBold(true);
            estiloCabecalho.setFont(negrito);
            estiloCabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloCabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> colunas = planilha.getNomesColunas();
            Row cabecalho = aba.createRow(0);
            for (int c = 0; c < colunas.size(); c++) {
                var celula = cabecalho.createCell(c);
                celula.setCellValue(colunas.get(c));
                celula.setCellStyle(estiloCabecalho);
            }
            int r = 1;
            for (Linha linha : planilha.getLinhas()) {
                Row row = aba.createRow(r++);
                for (int c = 0; c < colunas.size(); c++) {
                    row.createCell(c).setCellValue(linha.getValor(colunas.get(c)));
                }
            }
            aba.createFreezePane(0, 1);
            for (int c = 0; c < colunas.size(); c++) {
                aba.autoSizeColumn(c);
                if (aba.getColumnWidth(c) > MAX_LARGURA) {
                    aba.setColumnWidth(c, MAX_LARGURA);
                }
            }
            workbook.write(destino);
            workbook.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar o arquivo XLSX.", e);
        }
    }

    private static String nomeAba(String nome) {
        String limpo = nome.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (limpo.isEmpty()) {
            return "Planilha";
        }
        return limpo.length() > 31 ? limpo.substring(0, 31) : limpo;
    }
}
