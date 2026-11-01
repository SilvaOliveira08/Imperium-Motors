package br.com.dataflowauto.fichas.dominio;

/**
 * O que se sabe no início do preenchimento: a placa e, opcionalmente, o texto de um documento enviado
 * e o RENAVAM (muitas consultas de multas e débitos pedem placa + RENAVAM).
 */
public record ConsultaVeiculo(Placa placa, String textoDocumento, String nomeDocumento, String renavam) {

    public ConsultaVeiculo {
        renavam = renavam == null ? null : renavam.replaceAll("\\D", "");
        if (renavam != null && renavam.isEmpty()) {
            renavam = null;
        }
    }

    public ConsultaVeiculo(Placa placa, String textoDocumento, String nomeDocumento) {
        this(placa, textoDocumento, nomeDocumento, null);
    }

    public static ConsultaVeiculo somentePlaca(Placa placa) {
        return new ConsultaVeiculo(placa, null, null);
    }

    public boolean possuiRenavam() {
        return renavam != null;
    }

    public boolean possuiDocumento() {
        return textoDocumento != null && !textoDocumento.isBlank();
    }
}
