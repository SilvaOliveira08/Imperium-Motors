package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversões de valores em reais digitados no formulário ou lidos de planilhas. */
public final class Dinheiro {

    private Dinheiro() {
    }

    /**
     * Aceita "R$ 1.234,56", "1234,56", "1234.56" e "1.234". Quando há ponto e vírgula,
     * o último separador é o decimal; um único ponto seguido de 3 dígitos é separador de milhar.
     */
    public static BigDecimal interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.replaceAll("[^0-9,.\\-]", "");
        if (limpo.isEmpty() || limpo.equals("-")) {
            throw new RegraNegocioException("Valor inválido: \"" + texto.trim() + "\".");
        }
        int ultimaVirgula = limpo.lastIndexOf(',');
        int ultimoPonto = limpo.lastIndexOf('.');
        String normalizado;
        if (ultimaVirgula >= 0 && ultimoPonto >= 0) {
            normalizado = ultimaVirgula > ultimoPonto
                    ? limpo.replace(".", "").replace(',', '.')
                    : limpo.replace(",", "");
        } else if (ultimaVirgula >= 0) {
            normalizado = limpo.replace(".", "").replace(',', '.');
        } else if (ultimoPonto >= 0 && limpo.indexOf('.') == ultimoPonto && limpo.length() - ultimoPonto - 1 != 3) {
            normalizado = limpo;
        } else {
            normalizado = limpo.replace(".", "");
        }
        try {
            return new BigDecimal(normalizado).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("Valor inválido: \"" + texto.trim() + "\".");
        }
    }

    public static BigDecimal positivo(BigDecimal valor, String nomeCampo) {
        if (valor == null || valor.signum() <= 0) {
            throw new RegraNegocioException("Informe " + nomeCampo + " maior que zero.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal ouZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
