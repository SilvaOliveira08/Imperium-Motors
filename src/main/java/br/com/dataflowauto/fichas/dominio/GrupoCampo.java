package br.com.dataflowauto.fichas.dominio;

public enum GrupoCampo {
    IDENTIFICACAO("Identificação"),
    MOTOR("Motor e desempenho"),
    TRANSMISSAO("Transmissão"),
    DIMENSOES("Dimensões e pesos"),
    CAPACIDADES("Capacidades e consumo"),
    EQUIPAMENTOS("Equipamentos"),
    PROCEDENCIA("Procedência e histórico"),
    DOCUMENTACAO("Documentação, débitos e proprietários", false);

    private final String descricao;
    private final boolean publico;

    GrupoCampo(String descricao) {
        this(descricao, true);
    }

    GrupoCampo(String descricao, boolean publico) {
        this.descricao = descricao;
        this.publico = publico;
    }

    /** Grupos não públicos (dados de proprietários, multas, débitos) ficam só no painel da equipe. */
    public boolean isPublico() {
        return publico;
    }

    public String getDescricao() {
        return descricao;
    }
}
