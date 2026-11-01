package br.com.dataflowauto.fichas.dominio;

/** De onde veio o valor de um campo da ficha. */
public enum OrigemDado {
    USUARIO("Usuário"),
    DOCUMENTO("Documento"),
    FONTE_EXTERNA("Fonte externa");

    private final String descricao;

    OrigemDado(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
