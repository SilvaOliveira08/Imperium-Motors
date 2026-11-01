package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Remove linhas repetidas, mantendo a primeira ocorrência. Pode comparar todas as colunas
 * ou apenas as colunas-chave informadas (ex.: só o CPF). Linhas totalmente vazias saem; linhas com a
 * chave vazia são mantidas, porque não dá para saber se são repetidas.
 */
public class RemoverDuplicatas extends RegraTransformacao {

    private final List<String> colunasComparadas;

    public RemoverDuplicatas() {
        this(List.of());
    }

    public RemoverDuplicatas(List<String> colunasComparadas) {
        super(colunasComparadas.isEmpty()
                ? "Remover linhas duplicadas"
                : "Remover duplicatas por " + String.join(", ", colunasComparadas));
        this.colunasComparadas = List.copyOf(colunasComparadas);
    }

    @Override
    protected void validar(Planilha planilha) {
        colunasComparadas.forEach(planilha::getColuna);
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        List<String> colunas = colunasComparadas.isEmpty() ? planilha.getNomesColunas() : colunasComparadas;
        Set<List<String>> vistas = new HashSet<>();
        List<Linha> resultado = new ArrayList<>();
        for (Linha linha : planilha.getLinhas()) {
            if (planilha.getNomesColunas().stream().allMatch(linha::estaVazia)) {
                continue;
            }
            List<String> chave = colunas.stream()
                    .map(c -> linha.getValor(c).trim().toLowerCase(Locale.ROOT))
                    .toList();
            boolean chaveVazia = chave.stream().allMatch(String::isEmpty);
            if (chaveVazia || vistas.add(chave)) {
                resultado.add(linha);
            }
        }
        return planilha.comLinhas(resultado);
    }
}
