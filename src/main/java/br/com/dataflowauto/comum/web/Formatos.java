package br.com.dataflowauto.comum.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Formatação de valores nas telas, usada nos templates como {@code ${@fmt.moeda(valor)}}. */
@Component("fmt")
public class Formatos {

    private static final Locale BR = Locale.of("pt", "BR");

    public String moeda(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(BR);
        return formato.format(valor).replace(' ', ' ');
    }

    /** Moeda sem centavos, para preços em destaque. */
    public String preco(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(BR);
        formato.setMaximumFractionDigits(0);
        formato.setMinimumFractionDigits(0);
        return formato.format(valor.setScale(0, RoundingMode.HALF_UP)).replace(' ', ' ');
    }

    public String km(Integer valor) {
        return valor == null ? "—" : NumberFormat.getIntegerInstance(BR).format(valor) + " km";
    }

    /** Valor para preencher campos de formulário: "1234,56". */
    public String campo(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Cor da ilustração do carro (usada quando o anúncio não tem foto), a partir da cor da ficha. */
    public String corCarro(String cor) {
        String c = cor == null ? "" : java.text.Normalizer.normalize(cor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(BR);
        if (c.contains("branc")) return "#e9edf2";
        if (c.contains("pret")) return "#1d2129";
        if (c.contains("prat")) return "#b9c0c9";
        if (c.contains("cinz") || c.contains("grafite") || c.contains("chumbo")) return "#6b7480";
        if (c.contains("vermelh") || c.contains("carmim") || c.contains("vinho")) return "#c8102e";
        if (c.contains("azul")) return "#1f5fbf";
        if (c.contains("verde")) return "#2f7d4f";
        if (c.contains("amarel") || c.contains("dourad")) return "#e0a800";
        if (c.contains("laranj")) return "#e8590c";
        if (c.contains("marrom") || c.contains("bege")) return "#8a6a4f";
        return "#3a6fd8";
    }

    public String percentual(BigDecimal valor) {
        return valor == null ? "—" : valor.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
    }
}
