package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Leitura dos campos de texto dos formulários da loja. */
final class Conversoes {

    private Conversoes() {
    }

    static Integer inteiro(String texto, String nomeCampo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String digitos = texto.replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 9) {
            throw new RegraNegocioException("Valor inválido em " + nomeCampo + ".");
        }
        return Integer.parseInt(digitos);
    }

    /** Datas vêm do campo type=date (yyyy-MM-dd). */
    static LocalDate data(String texto, String nomeCampo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("Data inválida em " + nomeCampo + ".");
        }
    }

    static <E extends Enum<E>> E opcao(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Opção inválida: " + valor + ".");
        }
    }
}
