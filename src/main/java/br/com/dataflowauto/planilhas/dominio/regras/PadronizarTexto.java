package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.List;

/** Aplica uma padronização (CPF, placa, data, maiúsculas...) a todos os valores de uma coluna. */
public class PadronizarTexto extends RegraTransformacao {

    private final String coluna;
    private final TipoPadronizacao tipo;

    public PadronizarTexto(String coluna, TipoPadronizacao tipo) {
        super("Padronizar \"" + coluna + "\" como " + tipo.getDescricao());
        this.coluna = coluna;
        this.tipo = tipo;
    }

    @Override
    protected void validar(Planilha planilha) {
        planilha.getColuna(coluna);
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        List<Linha> linhas = planilha.getLinhas().stream()
                .map(l -> l.comValor(coluna, tipo.aplicar(l.getValor(coluna))))
                .toList();
        return planilha.comLinhas(linhas);
    }
}
