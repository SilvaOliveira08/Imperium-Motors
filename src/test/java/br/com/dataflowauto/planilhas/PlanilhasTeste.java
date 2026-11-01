package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilitário para montar planilhas pequenas nos testes. */
public final class PlanilhasTeste {

    private PlanilhasTeste() {
    }

    /** Ex.: planilha("Clientes", List.of("Nome", "CPF"), "Ana", "111", "Bruno", "222"). */
    public static Planilha planilha(String nome, List<String> colunas, String... valores) {
        List<Linha> linhas = new ArrayList<>();
        for (int i = 0; i < valores.length; i += colunas.size()) {
            Map<String, String> mapa = new LinkedHashMap<>();
            for (int c = 0; c < colunas.size(); c++) {
                mapa.put(colunas.get(c), valores[i + c]);
            }
            linhas.add(new Linha(mapa));
        }
        return Planilha.deNomes(nome, colunas, linhas);
    }

    public static List<String> coluna(Planilha planilha, String coluna) {
        return planilha.getLinhas().stream().map(l -> l.getValor(coluna)).toList();
    }
}
