package br.com.dataflowauto.planilhas.dominio.regras;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Padronizações disponíveis. Cada constante carrega sua própria função de conversão.
 * Valores que não podem ser convertidos (ex.: CPF com dígitos faltando) ficam como estão.
 */
public enum TipoPadronizacao {

    REMOVER_ESPACOS("Remover espaços extras", v -> v.trim().replaceAll("\\s+", " ")),
    MAIUSCULAS("MAIÚSCULAS", v -> v.toUpperCase(Locale.ROOT)),
    MINUSCULAS("minúsculas", v -> v.toLowerCase(Locale.ROOT)),
    NOME_PROPRIO("Nome Próprio", TipoPadronizacao::nomeProprio),
    SEM_ACENTOS("Remover acentos", v -> Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "")),
    CPF("CPF (000.000.000-00)", TipoPadronizacao::cpf),
    CNPJ("CNPJ (00.000.000/0000-00)", TipoPadronizacao::cnpj),
    PLACA("Placa (ABC1D23)", TipoPadronizacao::placa),
    TELEFONE("Telefone ((00) 00000-0000)", TipoPadronizacao::telefone),
    DATA("Data (dd/mm/aaaa)", TipoPadronizacao::data),
    SOMENTE_NUMEROS("Somente números", v -> v.replaceAll("\\D", ""));

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yy"));
    private static final DateTimeFormatter SAIDA_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String descricao;
    private final UnaryOperator<String> conversor;

    TipoPadronizacao(String descricao, UnaryOperator<String> conversor) {
        this.descricao = descricao;
        this.conversor = conversor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String aplicar(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor == null ? "" : valor;
        }
        return conversor.apply(valor);
    }

    private static String nomeProprio(String valor) {
        String[] partes = valor.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (List.of("da", "de", "do", "das", "dos", "e").contains(parte)) {
                sb.append(parte);
            } else {
                sb.append(Character.toUpperCase(parte.charAt(0))).append(parte.substring(1));
            }
        }
        return sb.toString();
    }

    private static String cpf(String valor) {
        String d = valor.replaceAll("\\D", "");
        if (d.length() != 11) {
            return valor;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private static String cnpj(String valor) {
        String d = valor.replaceAll("\\D", "");
        if (d.length() != 14) {
            return valor;
        }
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static String placa(String valor) {
        String p = valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return p.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}") ? p : valor;
    }

    private static String telefone(String valor) {
        String d = valor.replaceAll("\\D", "");
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) {
            d = d.substring(2);
        }
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return valor;
    }

    private static String data(String valor) {
        String v = valor.trim();
        if (v.length() > 10 && v.charAt(10) == 'T' || v.length() > 10 && v.charAt(10) == ' ') {
            v = v.substring(0, 10);
        }
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(v, formato).format(SAIDA_DATA);
            } catch (DateTimeParseException ignorada) {
                // tenta o próximo formato
            }
        }
        return valor;
    }
}
