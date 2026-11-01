package br.com.dataflowauto.comum.dominio;

public enum TipoOperacao {
    IMPORTACAO("Importação"),
    ORGANIZACAO("Organização"),
    COMBINACAO("Combinação"),
    EXPORTACAO("Exportação"),
    FICHA_CRIADA("Ficha criada"),
    FICHA_REVISADA("Ficha revisada"),
    FICHA_PDF("Ficha em PDF"),
    ANUNCIO("Anúncio"),
    DESPESA("Despesa"),
    PROPOSTA("Interessado"),
    VENDA("Venda"),
    DIVULGACAO("Divulgação");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
