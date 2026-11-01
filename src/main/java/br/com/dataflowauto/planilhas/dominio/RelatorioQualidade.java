package br.com.dataflowauto.planilhas.dominio;

import java.util.Map;

/**
 * Resultado da análise de qualidade de uma planilha.
 *
 * @param totalLinhas         linhas analisadas
 * @param linhasDuplicadas    linhas idênticas a uma linha anterior
 * @param vaziosPorColuna     quantidade de células vazias em cada coluna
 * @param linhasTotalmenteVazias linhas sem nenhum valor
 */
public record RelatorioQualidade(int totalLinhas,
                                 int linhasDuplicadas,
                                 Map<String, Integer> vaziosPorColuna,
                                 int linhasTotalmenteVazias) {

    public int totalVazios() {
        return vaziosPorColuna.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean semProblemas() {
        return linhasDuplicadas == 0 && totalVazios() == 0 && linhasTotalmenteVazias == 0;
    }

    public int percentualVazio(String coluna) {
        if (totalLinhas == 0) {
            return 0;
        }
        return (int) Math.round(100.0 * vaziosPorColuna.getOrDefault(coluna, 0) / totalLinhas);
    }
}
