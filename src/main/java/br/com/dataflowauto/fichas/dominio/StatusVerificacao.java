package br.com.dataflowauto.fichas.dominio;

/** Situação de um campo: só CONFIRMADO pode ser apresentado como dado verificado. */
public enum StatusVerificacao {
    CONFIRMADO("Confirmado"),
    A_CONFIRMAR("A confirmar"),
    AUSENTE("Ausente");

    private final String descricao;

    StatusVerificacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
