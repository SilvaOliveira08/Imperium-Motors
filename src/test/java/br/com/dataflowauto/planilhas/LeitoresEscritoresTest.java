package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.infra.EscritorCsv;
import br.com.dataflowauto.planilhas.infra.EscritorXlsx;
import br.com.dataflowauto.planilhas.infra.LeitorCsv;
import br.com.dataflowauto.planilhas.infra.LeitorXlsx;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.coluna;
import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitoresEscritoresTest {

    private final LeitorCsv leitorCsv = new LeitorCsv();
    private final LeitorXlsx leitorXlsx = new LeitorXlsx();

    @Test
    void csvComPontoEVirgulaEAcentosEmIso88591() {
        byte[] bytes = "Nome;Cidade\nJoão;São Paulo\n\nMaria;Campinas\n".getBytes(StandardCharsets.ISO_8859_1);
        Planilha p = leitorCsv.ler(new ByteArrayInputStream(bytes), "clientes");
        assertThat(p.getNomesColunas()).containsExactly("Nome", "Cidade");
        assertThat(coluna(p, "Cidade")).containsExactly("São Paulo", "Campinas");
    }

    @Test
    void csvComVirgulaAspasBomECabecalhoRepetido() {
        byte[] bytes = "﻿Nome,Nome,\n\"Silva, Ana\",x,y\nBruno".getBytes(StandardCharsets.UTF_8);
        Planilha p = leitorCsv.ler(new ByteArrayInputStream(bytes), "t");
        assertThat(p.getNomesColunas()).containsExactly("Nome", "Nome 2", "Coluna 3");
        assertThat(coluna(p, "Nome")).containsExactly("Silva, Ana", "Bruno");
        assertThat(coluna(p, "Nome 2")).containsExactly("x", "");
    }

    @Test
    void planilhaComMaisLinhasQueOTetoEhRecusada() throws Exception {
        byte[] csv = "Nome\nAna\nBia\nCaio\n".getBytes(StandardCharsets.UTF_8);
        assertThat(new LeitorCsv(3).ler(new ByteArrayInputStream(csv), "t").totalLinhas()).isEqualTo(3);
        assertThatThrownBy(() -> new LeitorCsv(2).ler(new ByteArrayInputStream(csv), "grande"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("mais de 2 linhas");

        ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
        new EscritorXlsx().escrever(new LeitorCsv().ler(new ByteArrayInputStream(csv), "t"), xlsx);
        assertThatThrownBy(() -> new LeitorXlsx(2).ler(new ByteArrayInputStream(xlsx.toByteArray()), "grande"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("mais de 2 linhas");
    }

    @Test
    void csvVazioEhRejeitado() {
        assertThatThrownBy(() -> leitorCsv.ler(new ByteArrayInputStream(new byte[0]), "vazio"))
                .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void xlsxComDatasNumerosEFormulas() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet aba = wb.createSheet("Dados");
            Row cab = aba.createRow(1); // cabeçalho na segunda linha
            cab.createCell(0).setCellValue("Produto");
            cab.createCell(1).setCellValue("Qtd");
            cab.createCell(2).setCellValue("Data");
            cab.createCell(3).setCellValue("Dobro");
            Row r = aba.createRow(2);
            r.createCell(0).setCellValue("Pneu");
            r.createCell(1).setCellValue(4);
            var estiloData = wb.createCellStyle();
            estiloData.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            var celData = r.createCell(2);
            celData.setCellValue(LocalDate.of(2024, 3, 5));
            celData.setCellStyle(estiloData);
            r.createCell(3).setCellFormula("B3*2");
            wb.write(saida);
        }
        Planilha p = leitorXlsx.ler(new ByteArrayInputStream(saida.toByteArray()), "estoque");
        assertThat(p.getNomesColunas()).containsExactly("Produto", "Qtd", "Data", "Dobro");
        assertThat(p.getLinhas().get(0).getValores())
                .containsEntry("Qtd", "4").containsEntry("Data", "05/03/2024").containsEntry("Dobro", "8");
    }

    @Test
    void arquivoQueNaoEhXlsxEhRejeitado() {
        assertThatThrownBy(() -> leitorXlsx.ler(new ByteArrayInputStream("não sou xlsx".getBytes()), "falso"))
                .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void xlsxIdaEVolta() {
        Planilha original = planilha("Clientes", List.of("Nome", "Placa"), "Ana", "ABC1D23", "Bruno", "");
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new EscritorXlsx().escrever(original, saida);
        Planilha lida = leitorXlsx.ler(new ByteArrayInputStream(saida.toByteArray()), "Clientes");
        assertThat(lida.getNomesColunas()).isEqualTo(original.getNomesColunas());
        assertThat(lida.getLinhas()).isEqualTo(original.getLinhas());
    }

    @Test
    void csvIdaEVoltaComProtecaoDeFormula() {
        Planilha original = planilha("P", List.of("Texto", "Numero"), "=HYPERLINK(\"x\")", "-15", "a;b", "3");
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new EscritorCsv().escrever(original, saida);
        String texto = saida.toString(StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿Texto;Numero");
        Planilha lida = leitorCsv.ler(new ByteArrayInputStream(saida.toByteArray()), "P");
        assertThat(coluna(lida, "Texto")).containsExactly("'=HYPERLINK(\"x\")", "a;b");
        assertThat(coluna(lida, "Numero")).containsExactly("-15", "3");
    }
}
