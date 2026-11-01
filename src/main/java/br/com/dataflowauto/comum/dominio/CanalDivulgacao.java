package br.com.dataflowauto.comum.dominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Onde a loja divulga os carros. Fica no pacote comum porque é usado pela divulgação
 * (o que foi publicado em cada canal) e pelas vendas (de qual canal veio cada interessado).
 * O código vai nos links ("?origem=instagram") para saber de onde o visitante chegou.
 */
public enum CanalDivulgacao {
    INSTAGRAM("Instagram", "instagram", false),
    FACEBOOK("Facebook", "facebook", false),
    MARKETPLACE("Facebook Marketplace", "marketplace", true),
    WHATSAPP("WhatsApp", "whatsapp", false),
    OLX("OLX", "olx", true),
    WEBMOTORS("Webmotors", "webmotors", true);

    private final String descricao;
    private final String codigo;
    private final boolean classificado;

    CanalDivulgacao(String descricao, String codigo, boolean classificado) {
        this.descricao = descricao;
        this.codigo = codigo;
        this.classificado = classificado;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Classificados pedem título e descrição separados; redes sociais usam um texto só. */
    public boolean isClassificado() {
        return classificado;
    }

    public static Optional<CanalDivulgacao> porCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String alvo = codigo.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.codigo.equals(alvo) || c.name().equalsIgnoreCase(alvo)).findFirst();
    }
}
