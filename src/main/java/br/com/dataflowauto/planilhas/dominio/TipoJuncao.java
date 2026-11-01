package br.com.dataflowauto.planilhas.dominio;

public enum TipoJuncao {
    /** Mantém só as linhas cuja chave existe nas duas planilhas. */
    INTERNA("Somente registros presentes nas duas"),
    /** Mantém todas as linhas da primeira planilha, completando com a segunda quando houver. */
    ESQUERDA("Todos da primeira, completando com a segunda"),
    /** Mantém todas as linhas das duas planilhas. */
    COMPLETA("Todos das duas planilhas");

    private final String descricao;

    TipoJuncao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
