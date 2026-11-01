package br.com.dataflowauto.planilhas.infra;

import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.planilhas.dominio.LeitorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê arquivos CSV detectando o separador (";", "," ou tabulação) e a codificação
 * (UTF-8 ou ISO-8859-1, comum em arquivos exportados pelo Excel em português).
 */
@Component
public class LeitorCsv implements LeitorPlanilha {

    private final int maximoLinhas;

    public LeitorCsv() {
        this(Planilha.MAXIMO_LINHAS);
    }

    /** Permite um teto menor (usado nos testes para não gerar 200 mil linhas). */
    public LeitorCsv(int maximoLinhas) {
        this.maximoLinhas = maximoLinhas;
    }

    @Override
    public boolean suporta(TipoArquivo tipo) {
        return tipo == TipoArquivo.CSV;
    }

    @Override
    public Planilha ler(InputStream conteudo, String nomePlanilha) {
        String texto;
        try {
            texto = decodificar(conteudo.readAllBytes());
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Não foi possível ler \"" + nomePlanilha + "\".", e);
        }
        if (texto.isBlank()) {
            throw new ArquivoInvalidoException("A planilha \"" + nomePlanilha + "\" está vazia.");
        }
        char separador = detectarSeparador(texto);
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(separador)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(texto), formato)) {
            Iterator<CSVRecord> registros = parser.iterator();
            List<String> brutos = new ArrayList<>();
            registros.next().forEach(brutos::add);
            List<String> cabecalhos = Cabecalhos.normalizar(brutos);

            List<Linha> linhas = new ArrayList<>();
            while (registros.hasNext()) {
                CSVRecord registro = registros.next();
                Map<String, String> valores = new LinkedHashMap<>();
                boolean algumValor = false;
                for (int i = 0; i < cabecalhos.size(); i++) {
                    String valor = i < registro.size() ? registro.get(i) : "";
                    algumValor |= !valor.isBlank();
                    valores.put(cabecalhos.get(i), valor);
                }
                if (algumValor) {
                    if (linhas.size() == maximoLinhas) {
                        throw Cabecalhos.linhasDemais(nomePlanilha, maximoLinhas);
                    }
                    linhas.add(new Linha(valores));
                }
            }
            return Planilha.deNomes(nomePlanilha, cabecalhos, linhas);
        } catch (IOException | java.io.UncheckedIOException | IllegalStateException e) {
            throw new ArquivoInvalidoException("O CSV \"" + nomePlanilha + "\" está mal formatado: " + e.getMessage(), e);
        }
    }

    static String decodificar(byte[] bytes) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    static char detectarSeparador(String texto) {
        int fim = texto.indexOf('\n');
        String primeiraLinha = fim < 0 ? texto : texto.substring(0, fim);
        char[] candidatos = {';', ',', '\t', '|'};
        char melhor = ',';
        long maior = 0;
        for (char c : candidatos) {
            long qtd = primeiraLinha.chars().filter(ch -> ch == c).count();
            if (qtd > maior) {
                maior = qtd;
                melhor = c;
            }
        }
        return melhor;
    }
}
