package br.com.dataflowauto.planilhas.infra;

import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.planilhas.dominio.EscritorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Grava CSV separado por ";" em UTF-8 com BOM, que o Excel em português abre corretamente.
 * Valores iniciados por "=", "+", "-" ou "@" seguidos de texto recebem um apóstrofo para não
 * serem executados como fórmula ao abrir o arquivo.
 */
@Component
public class EscritorCsv implements EscritorPlanilha {

    @Override
    public TipoArquivo formato() {
        return TipoArquivo.CSV;
    }

    @Override
    public String tipoConteudo() {
        return "text/csv; charset=UTF-8";
    }

    @Override
    public void escrever(Planilha planilha, OutputStream destino) {
        CSVFormat formato = CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n").build();
        try {
            Writer writer = new OutputStreamWriter(destino, StandardCharsets.UTF_8);
            writer.write('﻿');
            CSVPrinter printer = new CSVPrinter(writer, formato);
            List<String> colunas = planilha.getNomesColunas();
            printer.printRecord(colunas);
            for (Linha linha : planilha.getLinhas()) {
                printer.printRecord(colunas.stream().map(c -> protegerFormula(linha.getValor(c))).toList());
            }
            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar o arquivo CSV.", e);
        }
    }

    static String protegerFormula(String valor) {
        if (valor.isEmpty()) {
            return valor;
        }
        char primeiro = valor.charAt(0);
        boolean numero = valor.matches("[+-]?[0-9.,]+");
        if ((primeiro == '=' || primeiro == '@' || primeiro == '+' || primeiro == '-') && !numero) {
            return "'" + valor;
        }
        return valor;
    }
}
