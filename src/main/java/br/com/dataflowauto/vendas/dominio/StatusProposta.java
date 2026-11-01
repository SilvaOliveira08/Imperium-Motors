package br.com.dataflowauto.vendas.dominio;

public enum StatusProposta {
    NOVA("Nova", true),
    EM_ATENDIMENTO("Em atendimento", true),
    CONVERTIDA("Virou venda", false),
    DESCARTADA("Descartada", false);

    private final String descricao;
    private final boolean aberta;

    StatusProposta(String descricao, boolean aberta) {
        this.descricao = descricao;
        this.aberta = aberta;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isAberta() {
        return aberta;
    }
}
