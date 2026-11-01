package br.com.dataflowauto.vendas.dominio;

public enum TipoProposta {
    DUVIDA("Dúvida"),
    TEST_DRIVE("Agendar test drive"),
    PROPOSTA("Fazer proposta"),
    FINANCIAMENTO("Financiamento");

    private final String descricao;

    TipoProposta(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
