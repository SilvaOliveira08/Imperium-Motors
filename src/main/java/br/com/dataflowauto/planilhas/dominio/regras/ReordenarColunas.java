package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.planilhas.dominio.Coluna;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Coloca as colunas informadas primeiro, na ordem dada. Colunas não citadas
 * continuam na planilha, depois delas, na ordem original.
 */
public class ReordenarColunas extends RegraTransformacao {

    private final List<String> ordem;

    public ReordenarColunas(List<String> ordem) {
        super("Reordenar colunas: " + String.join(" → ", ordem));
        this.ordem = List.copyOf(ordem);
    }

    @Override
    protected void validar(Planilha planilha) {
        ordem.forEach(planilha::getColuna);
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        Set<String> nomes = new LinkedHashSet<>(ordem);
        nomes.addAll(planilha.getNomesColunas());
        List<Coluna> colunas = new ArrayList<>();
        nomes.forEach(n -> colunas.add(new Coluna(n)));
        return planilha.comColunasELinhas(colunas, planilha.getLinhas());
    }
}
