package br.com.dataflowauto.marketing.dominio;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;

/**
 * Texto pronto para um canal. Classificados (OLX, Webmotors, Marketplace) têm título separado;
 * redes sociais usam só o texto. O link já leva "?origem=" para saber de onde vieram os contatos.
 */
public record TextoDivulgacao(CanalDivulgacao canal, String titulo, String texto, String link, String dica) {

    public boolean temTitulo() {
        return titulo != null && !titulo.isBlank();
    }
}
