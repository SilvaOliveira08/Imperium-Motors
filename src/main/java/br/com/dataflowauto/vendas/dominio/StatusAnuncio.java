package br.com.dataflowauto.vendas.dominio;

/** Situação do carro na loja. Só DISPONIVEL e RESERVADO aparecem para o público. */
public enum StatusAnuncio {
    DISPONIVEL("Disponível", true),
    RESERVADO("Reservado", true),
    OCULTO("Fora da loja", false),
    VENDIDO("Vendido", false);

    private final String descricao;
    private final boolean visivelNaLoja;

    StatusAnuncio(String descricao, boolean visivelNaLoja) {
        this.descricao = descricao;
        this.visivelNaLoja = visivelNaLoja;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isVisivelNaLoja() {
        return visivelNaLoja;
    }
}
