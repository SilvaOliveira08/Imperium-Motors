package br.com.dataflowauto.marketing;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import br.com.dataflowauto.marketing.dominio.GeradorTextos;
import br.com.dataflowauto.marketing.dominio.Publicacao;
import br.com.dataflowauto.marketing.dominio.TextoDivulgacao;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.Venda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivulgacaoDominioTest {

    private static final String LINK = "https://loja.exemplo.com.br/loja/carros/7";
    private static final GeradorTextos.Vitrine LOJA = new GeradorTextos.Vitrine("Vértice Veículos",
            "Av. das Amoreiras, 1000 - Campinas/SP", "Campinas", "(19) 99999-0000", "1,79% a.m.");

    private static Anuncio polo() {
        FichaTecnica ficha = new FichaTecnica(new Veiculo(new Placa("ABC1D23")));
        Map<CampoTecnico, String> dados = new EnumMap<>(CampoTecnico.class);
        dados.put(CampoTecnico.MARCA, "Volkswagen");
        dados.put(CampoTecnico.MODELO, "Polo");
        dados.put(CampoTecnico.VERSAO, "Highline");
        dados.put(CampoTecnico.ANO_FABRICACAO, "2023");
        dados.put(CampoTecnico.ANO_MODELO, "2024");
        dados.put(CampoTecnico.MOTORIZACAO, "1.0 TSI");
        dados.put(CampoTecnico.COMBUSTIVEL, "Flex");
        dados.put(CampoTecnico.CAMBIO, "Automático de 6 marchas");
        dados.put(CampoTecnico.COR, "Branco");
        dados.put(CampoTecnico.QUANTIDADE_DONOS, "1");
        dados.put(CampoTecnico.LEILAO, "Nada consta");
        dados.put(CampoTecnico.EQUIPAMENTOS, "Multimídia, câmera de ré, 6 airbags, faróis LED, teto solar");
        dados.forEach(ficha::editar);
        ficha.concluir();
        return new Anuncio(ficha, new DadosAnuncio(null, new BigDecimal("99900"), 18500, new BigDecimal("80000"),
                LocalDate.now().minusDays(20), "Único dono.", false));
    }

    private static TextoDivulgacao texto(List<TextoDivulgacao> textos, CanalDivulgacao canal) {
        return textos.stream().filter(t -> t.canal() == canal).findFirst().orElseThrow();
    }

    @Test
    void geraUmTextoPorCanalComPrecoProcedenciaEOrigemNoLink() {
        List<TextoDivulgacao> textos = new GeradorTextos().gerar(polo(), LOJA, LINK, new BigDecimal("2190.55"));
        assertThat(textos).extracting(TextoDivulgacao::canal).containsExactly(CanalDivulgacao.values());

        TextoDivulgacao instagram = texto(textos, CanalDivulgacao.INSTAGRAM);
        assertThat(instagram.texto()).contains("Volkswagen Polo Highline", "2023/2024", "18.500 km", "R$ 99.900 à vista",
                "48x de R$ 2.190,55", "1 dono, sem leilão", "link da bio", "taxa de 1,79% a.m., sujeita", "#volkswagen", "#polo", "#campinas")
                .doesNotContain("http").doesNotContain("teto solar"); // só os 4 primeiros equipamentos
        assertThat(instagram.link()).isEqualTo(LINK + "?origem=instagram");

        assertThat(texto(textos, CanalDivulgacao.FACEBOOK).texto()).contains(LINK + "?origem=facebook");
        assertThat(texto(textos, CanalDivulgacao.WHATSAPP).texto()).startsWith("*Volkswagen Polo Highline")
                .contains(LINK + "?origem=whatsapp");
    }

    @Test
    void classificadosTemTituloESemContatoNoTexto() {
        List<TextoDivulgacao> textos = new GeradorTextos().gerar(polo(), LOJA, LINK, null);
        TextoDivulgacao olx = texto(textos, CanalDivulgacao.OLX);
        assertThat(olx.titulo()).isEqualTo("Volkswagen Polo Highline 2024");
        assertThat(olx.texto()).contains("Câmbio: Automático de 6 marchas", "Procedência: 1 dono, sem leilão")
                .doesNotContain("http").doesNotContain("99999-0000").doesNotContain("✅");
        assertThat(texto(textos, CanalDivulgacao.WEBMOTORS).texto()).doesNotContain("99999-0000");
        // no Marketplace o WhatsApp da loja pode ir no texto
        assertThat(texto(textos, CanalDivulgacao.MARKETPLACE).texto()).contains("WhatsApp (19) 99999-0000");
        // sem parcela calculada, nenhum texto promete parcela
        assertThat(texto(textos, CanalDivulgacao.INSTAGRAM).texto()).doesNotContain("48x");
    }

    @Test
    void publicacaoViraPendenciaQuandoOCarroEVendido() {
        Anuncio anuncio = polo();
        Publicacao olx = new Publicacao(anuncio, CanalDivulgacao.OLX, " https://www.olx.com.br/anuncio/123 ", LocalDate.now());
        assertThat(olx.getLink()).isEqualTo("https://www.olx.com.br/anuncio/123");
        assertThat(olx.isAtiva()).isTrue();
        assertThat(olx.isPendenteRemocao()).isFalse();

        anuncio.registrarVenda(new Venda("Ana", null, new BigDecimal("98000"), LocalDate.now(), FormaPagamento.PIX));
        assertThat(olx.isPendenteRemocao()).isTrue();

        olx.tirarDoAr(LocalDate.now());
        assertThat(olx.isAtiva()).isFalse();
        assertThat(olx.isPendenteRemocao()).isFalse();
        assertThatThrownBy(() -> olx.tirarDoAr(LocalDate.now())).isInstanceOf(RegraNegocioException.class);

        olx.publicar(null, LocalDate.now());
        assertThat(olx.isAtiva()).isTrue();
        assertThat(olx.getLink()).isNull();
    }

    @Test
    void linkDoAnuncioPrecisaSerEnderecoWeb() {
        assertThatThrownBy(() -> new Publicacao(polo(), CanalDivulgacao.OLX, "olx.com.br/123", LocalDate.now()))
                .hasMessageContaining("http");
        assertThatThrownBy(() -> new Publicacao(polo(), CanalDivulgacao.OLX, "javascript:alert(1)", LocalDate.now()))
                .hasMessageContaining("http");
    }

    @Test
    void canalPorCodigoDoLink() {
        assertThat(CanalDivulgacao.porCodigo("instagram")).contains(CanalDivulgacao.INSTAGRAM);
        assertThat(CanalDivulgacao.porCodigo(" OLX ")).contains(CanalDivulgacao.OLX);
        assertThat(CanalDivulgacao.porCodigo("tiktok")).isEmpty();
        assertThat(CanalDivulgacao.porCodigo(null)).isEmpty();
    }
}
