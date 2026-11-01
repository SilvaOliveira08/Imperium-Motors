package br.com.dataflowauto.planilhas.infra;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.planilhas.dominio.LeitorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lê a primeira aba de um arquivo .xlsx com Apache POI. A primeira linha preenchida é o cabeçalho. */
@Component
public class LeitorXlsx implements LeitorPlanilha {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DataFormatter formatador = new DataFormatter(Locale.of("pt", "BR"));
    private final int maximoLinhas;

    public LeitorXlsx() {
        this(Planilha.MAXIMO_LINHAS);
    }

    /** Permite um teto menor (usado nos testes para não gerar 200 mil linhas). */
    public LeitorXlsx(int maximoLinhas) {
        this.maximoLinhas = maximoLinhas;
    }

    @Override
    public boolean suporta(TipoArquivo tipo) {
        return tipo == TipoArquivo.XLSX;
    }

    @Override
    public Planilha ler(InputStream conteudo, String nomePlanilha) {
        return lerAba(conteudo, nomePlanilha, null, null);
    }

    /**
     * Lê uma aba específica (se existir; senão a primeira). Com {@code colunaCabecalho}, o cabeçalho
     * é a primeira linha que tem uma célula com esse texto, o que permite pular títulos acima da tabela,
     * como na planilha de Controle de Despesas.
     */
    public Planilha lerAba(InputStream conteudo, String nomePlanilha, String nomeAba, String colunaCabecalho) {
        try (Workbook workbook = new XSSFWorkbook(conteudo)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ArquivoInvalidoException("O arquivo não tem nenhuma aba.");
            }
            Sheet aba = nomeAba != null && workbook.getSheet(nomeAba) != null
                    ? workbook.getSheet(nomeAba)
                    : workbook.getSheetAt(0);
            FormulaEvaluator avaliador = workbook.getCreationHelper().createFormulaEvaluator();

            Row linhaCabecalho = null;
            int indice = aba.getFirstRowNum();
            for (; indice <= aba.getLastRowNum(); indice++) {
                Row r = aba.getRow(indice);
                if (r != null && !linhaVazia(r, avaliador)
                        && (colunaCabecalho == null || contemTexto(r, colunaCabecalho, avaliador))) {
                    linhaCabecalho = r;
                    break;
                }
            }
            if (linhaCabecalho == null) {
                throw new ArquivoInvalidoException(colunaCabecalho == null
                        ? "A planilha \"" + nomePlanilha + "\" está vazia."
                        : "Não encontrei a coluna \"" + colunaCabecalho + "\" em \"" + nomePlanilha + "\".");
            }

            int totalColunas = linhaCabecalho.getLastCellNum();
            List<String> brutos = new ArrayList<>();
            for (int c = 0; c < totalColunas; c++) {
                brutos.add(valor(linhaCabecalho.getCell(c), avaliador));
            }
            List<String> cabecalhos = Cabecalhos.normalizar(brutos);

            List<Linha> linhas = new ArrayList<>();
            for (int r = indice + 1; r <= aba.getLastRowNum(); r++) {
                Row row = aba.getRow(r);
                if (row == null || linhaVazia(row, avaliador)) {
                    continue;
                }
                if (linhas.size() == maximoLinhas) {
                    throw Cabecalhos.linhasDemais(nomePlanilha, maximoLinhas);
                }
                Map<String, String> valores = new LinkedHashMap<>();
                for (int c = 0; c < cabecalhos.size(); c++) {
                    valores.put(cabecalhos.get(c), valor(row.getCell(c), avaliador));
                }
                linhas.add(new Linha(valores));
            }
            return Planilha.deNomes(nomePlanilha, cabecalhos, linhas);
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw new ArquivoInvalidoException("Não foi possível ler \"" + nomePlanilha + "\" como XLSX.", e);
        }
    }

    private boolean contemTexto(Row row, String texto, FormulaEvaluator avaliador) {
        for (Cell cell : row) {
            if (valor(cell, avaliador).equalsIgnoreCase(texto)) {
                return true;
            }
        }
        return false;
    }

    private boolean linhaVazia(Row row, FormulaEvaluator avaliador) {
        for (Cell cell : row) {
            if (!valor(cell, avaliador).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String valor(Cell cell, FormulaEvaluator avaliador) {
        if (cell == null) {
            return "";
        }
        CellType tipo = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (tipo == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(FORMATO_DATA);
        }
        try {
            return formatador.formatCellValue(cell, avaliador).trim();
        } catch (RuntimeException e) {
            return formatador.formatCellValue(cell).trim();
        }
    }
}
