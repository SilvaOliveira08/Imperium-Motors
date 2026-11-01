package br.com.dataflowauto.fichas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.util.Locale;

/** Placa de veículo no padrão antigo (ABC1234) ou Mercosul (ABC1D23). */
public record Placa(String valor) {

    public Placa {
        if (valor == null) {
            throw new RegraNegocioException("Informe a placa do veículo.");
        }
        valor = valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!valor.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}")) {
            throw new RegraNegocioException("Placa inválida. Use o formato ABC1234 ou ABC1D23.");
        }
    }

    public boolean ehMercosul() {
        return Character.isLetter(valor.charAt(4));
    }

    public String formatada() {
        return ehMercosul() ? valor : valor.substring(0, 3) + "-" + valor.substring(3);
    }

    /** Último dígito, o único pedaço da placa que pode aparecer em material público (rodízio, licenciamento). */
    public String finalDaPlaca() {
        return valor.substring(valor.length() - 1);
    }

    @Override
    public String toString() {
        return valor;
    }
}
