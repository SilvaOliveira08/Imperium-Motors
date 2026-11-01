package br.com.dataflowauto.marketing.dominio;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.vendas.dominio.Anuncio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monta os textos de divulgação de um carro a partir da ficha confirmada e do anúncio.
 * Cada canal tem seu jeito: Instagram com emojis e hashtags (link fica na bio), Facebook com link
 * clicável, WhatsApp curto com negrito, e classificados com título e descrição sem emojis.
 * Nos classificados não entram telefone nem links: esses sites costumam recusar contato no texto.
 */
public class GeradorTextos {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final int MAXIMO_EQUIPAMENTOS = 4;
    /** O formato de moeda do Java põe um espaço que não quebra depois do "R$"; nos posts vai espaço comum. */
    private static final char ESPACO_FIXO = (char) 160;

    /** O que o texto precisa saber da loja. */
    public record Vitrine(String nomeLoja, String endereco, String cidade, String whatsapp, String taxaFormatada) {
    }

    public List<TextoDivulgacao> gerar(Anuncio anuncio, Vitrine loja, String linkCarro, BigDecimal parcela) {
        List<TextoDivulgacao> textos = new ArrayList<>();
        for (CanalDivulgacao canal : CanalDivulgacao.values()) {
            String link = linkCarro + "?origem=" + canal.getCodigo();
            textos.add(switch (canal) {
                case INSTAGRAM -> new TextoDivulgacao(canal, null, instagram(anuncio, loja, parcela), link,
                        "Legenda para o post ou reels. Coloque o link na bio ou nos stories (link abaixo).");
                case FACEBOOK -> new TextoDivulgacao(canal, null, facebook(anuncio, loja, parcela, link), link,
                        "Post na página da loja ou em grupos de compra e venda.");
                case WHATSAPP -> new TextoDivulgacao(canal, null, whatsapp(anuncio, parcela, link), link,
                        "Para status, lista de transmissão ou responder quem pediu o carro.");
                case MARKETPLACE -> new TextoDivulgacao(canal, titulo(anuncio), classificado(anuncio, loja, true), link,
                        "Preencha os campos do veículo com os dados ao lado e cole título e descrição.");
                case OLX, WEBMOTORS -> new TextoDivulgacao(canal, titulo(anuncio), classificado(anuncio, loja, false), link,
                        "Sem telefone e sem links no texto: o contato fica nos campos do próprio site.");
            });
        }
        return textos;
    }

    String titulo(Anuncio a) {
        return (a.getTitulo() + " " + a.getAnoModelo()).trim();
    }

    private String instagram(Anuncio a, Vitrine loja, BigDecimal parcela) {
        StringBuilder t = new StringBuilder();
        t.append("🚗 ").append(a.getTitulo()).append(" · ").append(a.getAnos()).append("\n\n");
        destaquesComMarcador(a).forEach(d -> t.append("✅ ").append(d).append('\n'));
        t.append('\n');
        t.append("💰 ").append(preco(a.getPreco())).append(" à vista\n");
        if (parcela != null) {
            t.append("💳 ou 48x de ").append(moeda(parcela)).append(" com 30% de entrada*\n");
        }
        List<String> equipamentos = equipamentos(a);
        if (!equipamentos.isEmpty()) {
            t.append("\n⭐ ").append(String.join(", ", equipamentos)).append('\n');
        }
        t.append("\nFicha técnica conferida e histórico consultado pela nossa equipe.\n\n");
        t.append("📍 ").append(loja.nomeLoja());
        if (!vazio(loja.endereco())) {
            t.append(" · ").append(loja.endereco());
        }
        t.append('\n');
        if (!vazio(loja.whatsapp())) {
            t.append("💬 WhatsApp ").append(loja.whatsapp()).append('\n');
        }
        t.append("🔗 Fotos, ficha completa e simulação no link da bio.\n");
        if (parcela != null) {
            t.append("\n*Simulação com taxa de ").append(loja.taxaFormatada()).append(", sujeita à análise de crédito.\n");
        }
        t.append('\n').append(hashtags(a, loja, true));
        return t.toString().trim();
    }

    private String facebook(Anuncio a, Vitrine loja, BigDecimal parcela, String link) {
        StringBuilder t = new StringBuilder();
        t.append("🚗 ").append(a.getTitulo()).append(" · ").append(a.getAnos()).append("\n\n");
        destaquesComMarcador(a).forEach(d -> t.append("✅ ").append(d).append('\n'));
        t.append("\n💰 ").append(preco(a.getPreco())).append(" à vista");
        if (parcela != null) {
            t.append(" ou 48x de ").append(moeda(parcela)).append(" com 30% de entrada (sujeito à análise de crédito)");
        }
        t.append("\n\n");
        if (!vazio(a.getDescricao())) {
            t.append(a.getDescricao()).append("\n\n");
        }
        t.append("👉 Fotos, ficha técnica completa e simulação: ").append(link).append('\n');
        if (!vazio(loja.whatsapp())) {
            t.append("💬 WhatsApp ").append(loja.whatsapp()).append('\n');
        }
        t.append("📍 ").append(loja.nomeLoja());
        if (!vazio(loja.endereco())) {
            t.append(" · ").append(loja.endereco());
        }
        t.append("\n\n").append(hashtags(a, loja, false));
        return t.toString().trim();
    }

    private String whatsapp(Anuncio a, BigDecimal parcela, String link) {
        StringBuilder t = new StringBuilder();
        t.append('*').append(a.getTitulo()).append(" · ").append(a.getAnos()).append("*\n");
        t.append(String.join(" · ", resumoTecnico(a))).append('\n');
        if (!a.getProcedencia().isEmpty()) {
            t.append("✅ ").append(capitalizar(String.join(", ", a.getProcedencia()))).append('\n');
        }
        t.append("💰 *").append(preco(a.getPreco())).append("* à vista");
        if (parcela != null) {
            t.append(" ou 48x de ").append(moeda(parcela));
        }
        t.append("\n\nFotos e ficha completa: ").append(link);
        return t.toString();
    }

    /** Descrição de classificado: texto corrido, sem emojis. No Marketplace dá para pôr o WhatsApp da loja. */
    private String classificado(Anuncio a, Vitrine loja, boolean comContato) {
        StringBuilder t = new StringBuilder();
        t.append(a.getTitulo()).append(", ").append(a.getAnos()).append(", com ").append(km(a.getQuilometragem())).append(".\n\n");
        linha(t, "Câmbio", a.getCambio());
        linha(t, "Combustível", a.getCombustivel());
        linha(t, "Cor", a.getCor());
        linha(t, "Motor", a.dado(CampoTecnico.MOTORIZACAO));
        linha(t, "Lugares", a.dado(CampoTecnico.LUGARES));
        if (!a.getProcedencia().isEmpty()) {
            t.append("Procedência: ").append(String.join(", ", a.getProcedencia())).append(" (histórico consultado pela loja).\n");
        }
        List<String> equipamentos = equipamentos(a);
        if (!equipamentos.isEmpty()) {
            t.append("Equipamentos: ").append(String.join(", ", equipamentos)).append(".\n");
        }
        if (!vazio(a.getDescricao())) {
            t.append('\n').append(a.getDescricao()).append('\n');
        }
        t.append("\nFicha técnica conferida pela equipe. Financiamos com entrada a partir de 30% ")
                .append("(sujeito à análise de crédito). Aceitamos seu usado na troca.");
        if (comContato) {
            t.append("\n\n").append(loja.nomeLoja());
            if (!vazio(loja.endereco())) {
                t.append(" · ").append(loja.endereco());
            }
            if (!vazio(loja.whatsapp())) {
                t.append("\nWhatsApp ").append(loja.whatsapp());
            }
        }
        return t.toString().trim();
    }

    private List<String> destaquesComMarcador(Anuncio a) {
        List<String> itens = new ArrayList<>();
        itens.add(km(a.getQuilometragem()));
        if (!vazio(a.getCambio())) {
            itens.add("Câmbio " + a.getCambio().substring(0, 1).toLowerCase(BR) + a.getCambio().substring(1));
        }
        if (!vazio(a.getCombustivel())) {
            itens.add(a.getCombustivel());
        }
        if (!vazio(a.getCor())) {
            itens.add(a.getCor());
        }
        if (!a.getProcedencia().isEmpty()) {
            itens.add(capitalizar(String.join(", ", a.getProcedencia())));
        }
        return itens;
    }

    private List<String> resumoTecnico(Anuncio a) {
        List<String> itens = new ArrayList<>();
        itens.add(km(a.getQuilometragem()));
        if (!vazio(a.getTipoCambio())) {
            itens.add(a.getTipoCambio());
        }
        if (!vazio(a.getCombustivel())) {
            itens.add(a.getCombustivel());
        }
        return itens;
    }

    private List<String> equipamentos(Anuncio a) {
        String lista = a.dado(CampoTecnico.EQUIPAMENTOS);
        if (vazio(lista)) {
            return List.of();
        }
        return Arrays.stream(lista.split("[,;]")).map(String::trim).filter(e -> !e.isEmpty())
                .limit(MAXIMO_EQUIPAMENTOS).toList();
    }

    private String hashtags(Anuncio a, Vitrine loja, boolean completo) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("seminovos");
        if (completo) {
            tags.add("carrosseminovos");
            tags.add("carrosusados");
        }
        tags.add(tag(a.getMarca()));
        tags.add(tag(a.getModelo()));
        tags.add(tag(loja.cidade()));
        if (completo) {
            tags.add(tag(loja.nomeLoja()));
        }
        return tags.stream().filter(t -> !t.isEmpty()).map(t -> "#" + t).collect(Collectors.joining(" "));
    }

    private static String tag(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(BR).replaceAll("[^a-z0-9]", "");
    }

    private static void linha(StringBuilder t, String rotulo, String valor) {
        if (!vazio(valor)) {
            t.append(rotulo).append(": ").append(valor).append('\n');
        }
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : texto.substring(0, 1).toUpperCase(BR) + texto.substring(1);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String preco(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(BR);
        formato.setMaximumFractionDigits(0);
        formato.setMinimumFractionDigits(0);
        return formato.format(valor.setScale(0, RoundingMode.HALF_UP)).replace(ESPACO_FIXO, ' ');
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(BR).format(valor).replace(ESPACO_FIXO, ' ');
    }

    private static String km(Integer valor) {
        return NumberFormat.getIntegerInstance(BR).format(valor == null ? 0 : valor) + " km";
    }
}
