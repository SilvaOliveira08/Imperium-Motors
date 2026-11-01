package br.com.dataflowauto.planilhas.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Uma linha de dados: valores indexados pelo nome da coluna. Imutável. */
public final class Linha {

    private final Map<String, String> valores;

    public Linha(Map<String, String> valores) {
        this.valores = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(valores)));
    }

    /** Valor da célula, ou texto vazio quando a coluna não tem valor nesta linha. */
    public String getValor(String coluna) {
        String valor = valores.get(coluna);
        return valor == null ? "" : valor;
    }

    public boolean estaVazia(String coluna) {
        return getValor(coluna).isBlank();
    }

    public Map<String, String> getValores() {
        return valores;
    }

    public Linha comValor(String coluna, String valor) {
        Map<String, String> copia = new LinkedHashMap<>(valores);
        copia.put(coluna, valor);
        return new Linha(copia);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Linha outra && valores.equals(outra.valores);
    }

    @Override
    public int hashCode() {
        return valores.hashCode();
    }
}
