package br.com.dataflowauto.planilhas.dominio;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Identifica duplicatas e campos vazios sem alterar a planilha. */
public class AnalisadorQualidade {

    public RelatorioQualidade analisar(Planilha planilha) {
        Map<String, Integer> vazios = new LinkedHashMap<>();
        planilha.getNomesColunas().forEach(c -> vazios.put(c, 0));

        Set<Map<String, String>> vistas = new HashSet<>();
        int duplicadas = 0;
        int totalmenteVazias = 0;

        for (Linha linha : planilha.getLinhas()) {
            Map<String, String> assinatura = new LinkedHashMap<>();
            boolean algumValor = false;
            for (String coluna : planilha.getNomesColunas()) {
                String valor = linha.getValor(coluna).trim();
                assinatura.put(coluna, valor);
                if (valor.isEmpty()) {
                    vazios.merge(coluna, 1, Integer::sum);
                } else {
                    algumValor = true;
                }
            }
            if (!algumValor) {
                totalmenteVazias++;
            } else if (!vistas.add(assinatura)) {
                duplicadas++;
            }
        }
        return new RelatorioQualidade(planilha.totalLinhas(), duplicadas, vazios, totalmenteVazias);
    }
}
