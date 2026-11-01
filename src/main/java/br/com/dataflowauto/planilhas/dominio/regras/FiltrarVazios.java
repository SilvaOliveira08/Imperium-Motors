package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.planilhas.dominio.Planilha;

/** Remove as linhas em que a coluna informada está vazia (ex.: clientes sem CPF). */
public class FiltrarVazios extends RegraTransformacao {

    private final String coluna;

    public FiltrarVazios(String coluna) {
        super("Remover linhas com \"" + coluna + "\" vazio");
        this.coluna = coluna;
    }

    @Override
    protected void validar(Planilha planilha) {
        planilha.getColuna(coluna);
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        return planilha.comLinhas(planilha.getLinhas().stream().filter(l -> !l.estaVazia(coluna)).toList());
    }
}
