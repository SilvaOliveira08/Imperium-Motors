package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.planilhas.dominio.Planilha;

/**
 * Uma etapa de organização aplicada sobre a planilha.
 * Cada subclasse implementa uma transformação diferente; o serviço só conhece este contrato
 * e executa uma lista de regras em sequência (polimorfismo).
 */
public abstract class RegraTransformacao {

    private final String descricao;

    protected RegraTransformacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Aplica a regra e devolve uma nova planilha; a original não é alterada. */
    public final Planilha aplicar(Planilha planilha) {
        validar(planilha);
        return transformar(planilha);
    }

    /** Ponto de extensão para checar pré-condições (ex.: colunas existentes). */
    protected void validar(Planilha planilha) {
    }

    protected abstract Planilha transformar(Planilha planilha);

    @Override
    public String toString() {
        return descricao;
    }
}
