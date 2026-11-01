package br.com.dataflowauto.planilhas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.util.Objects;

/** Uma coluna da planilha, identificada pelo nome do cabeçalho. Imutável. */
public final class Coluna {

    private final String nome;

    public Coluna(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("O nome da coluna não pode ser vazio.");
        }
        this.nome = nome.trim();
    }

    public String getNome() {
        return nome;
    }

    public Coluna renomear(String novoNome) {
        return new Coluna(novoNome);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Coluna outra && nome.equals(outra.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nome);
    }

    @Override
    public String toString() {
        return nome;
    }
}
