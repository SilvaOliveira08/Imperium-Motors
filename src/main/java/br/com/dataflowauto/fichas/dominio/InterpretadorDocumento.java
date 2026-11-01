package br.com.dataflowauto.fichas.dominio;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê texto livre de um documento (CRLV, folheto, ficha do fabricante) procurando
 * linhas no formato "Rótulo: valor" ou "Rótulo  valor". Também entende o campo
 * combinado "Marca/Modelo" usado no CRLV (ex.: "VW/GOL 1.0").
 */
public class InterpretadorDocumento {

    private static final Pattern ROTULO_VALOR = Pattern.compile("^\\s*([\\p{L}][\\p{L}0-9 ./\\-]{1,40}?)\\s*(?::|=|\\t| {2,})\\s*(.+?)\\s*$");
    private static final Pattern MARCA_MODELO = Pattern.compile("^\\s*marca\\s*/\\s*modelo(?:\\s*/\\s*vers[aã]o)?\\s*[:=]?\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANO_FAB_MOD = Pattern.compile("^\\s*ano\\s+fab(?:rica[cç][aã]o)?\\s*/\\s*mod(?:elo)?\\s*[:=]?\\s*(\\d{4})\\s*/\\s*(\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public Map<CampoTecnico, String> interpretar(String texto) {
        Map<CampoTecnico, String> encontrados = new EnumMap<>(CampoTecnico.class);
        if (texto == null) {
            return encontrados;
        }
        for (String linha : texto.split("\\R")) {
            if (linha.isBlank()) {
                continue;
            }
            if (interpretarMarcaModelo(linha, encontrados) || interpretarAnos(linha, encontrados)) {
                continue;
            }
            Matcher m = ROTULO_VALOR.matcher(linha);
            if (m.matches()) {
                Optional<CampoTecnico> campo = CampoTecnico.porRotulo(m.group(1));
                String valor = m.group(2).trim();
                campo.filter(c -> !valor.isEmpty()).ifPresent(c -> encontrados.putIfAbsent(c, valor));
            }
        }
        return encontrados;
    }

    private boolean interpretarMarcaModelo(String linha, Map<CampoTecnico, String> encontrados) {
        Matcher m = MARCA_MODELO.matcher(linha);
        if (!m.matches()) {
            return false;
        }
        String[] partes = m.group(1).trim().split("\\s*/\\s*", 2);
        encontrados.putIfAbsent(CampoTecnico.MARCA, partes[0].trim());
        if (partes.length > 1 && !partes[1].isBlank()) {
            encontrados.putIfAbsent(CampoTecnico.MODELO, partes[1].trim());
        }
        return true;
    }

    private boolean interpretarAnos(String linha, Map<CampoTecnico, String> encontrados) {
        Matcher m = ANO_FAB_MOD.matcher(linha);
        if (!m.find()) {
            return false;
        }
        encontrados.putIfAbsent(CampoTecnico.ANO_FABRICACAO, m.group(1));
        encontrados.putIfAbsent(CampoTecnico.ANO_MODELO, m.group(2));
        return true;
    }
}
