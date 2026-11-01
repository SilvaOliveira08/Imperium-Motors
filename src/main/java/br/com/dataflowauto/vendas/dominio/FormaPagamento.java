package br.com.dataflowauto.vendas.dominio;

public enum FormaPagamento {
    A_VISTA("À vista"),
    PIX("PIX"),
    FINANCIAMENTO("Financiamento"),
    CONSORCIO("Consórcio"),
    TROCA_COM_TROCO("Troca + diferença"),
    CARTAO("Cartão de crédito");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
