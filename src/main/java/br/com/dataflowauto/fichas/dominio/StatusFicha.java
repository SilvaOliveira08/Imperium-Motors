package br.com.dataflowauto.fichas.dominio;

public enum StatusFicha {
    EM_REVISAO("Em revisão"),
    CONCLUIDA("Concluída");

    private final String descricao;

    StatusFicha(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
