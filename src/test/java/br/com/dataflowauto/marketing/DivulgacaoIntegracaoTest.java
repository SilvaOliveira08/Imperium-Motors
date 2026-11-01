package br.com.dataflowauto.marketing;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.marketing.infra.PublicacaoRepository;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Divulgação de ponta a ponta: kit do carro, publicar, contato que chega pelo link, venda e pendência. */
@SpringBootTest
class DivulgacaoIntegracaoTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private FichaService fichas;
    @Autowired
    private PublicacaoRepository publicacoes;
    @Autowired
    private PropostaRepository propostas;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private long anunciar(String placa) throws Exception {
        FichaTecnica ficha = fichas.criar(new NovaFichaComando(placa, null, Map.of(
                CampoTecnico.MARCA, "Fiat", CampoTecnico.MODELO, "Pulse", CampoTecnico.ANO_MODELO, "2023",
                CampoTecnico.MOTORIZACAO, "1.0 Turbo", CampoTecnico.COMBUSTIVEL, "Flex", CampoTecnico.CAMBIO, "CVT")));
        fichas.revisar(ficha.getId(), Map.of(), EnumSet.allOf(CampoTecnico.class), true);
        MvcResult criado = mvc.perform(multipart("/anuncios")
                        .file(new MockMultipartFile("fotos", "frente.png", "image/png", PNG))
                        .param("fichaId", String.valueOf(ficha.getId()))
                        .param("preco", "105.000").param("quilometragem", "12.000")
                        .param("valorCompra", "90.000").param("dataCompra", LocalDate.now().minusDays(10).toString())
                        .with(user("admin")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String destino = criado.getResponse().getRedirectedUrl();
        return Long.parseLong(destino.substring(destino.lastIndexOf('/') + 1));
    }

    @Test
    void divulgacaoExigeLogin() throws Exception {
        mvc.perform(get("/divulgacao")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/entrar"));
    }

    @Test
    void kitPublicacaoOrigemDoContatoEPendenciaAoVender() throws Exception {
        var admin = user("admin").roles("ADMIN");
        long id = anunciar("PLS3D21");

        // kit com textos por canal, links marcados e fotos para baixar
        mvc.perform(get("/divulgacao/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Textos prontos por canal")))
                .andExpect(content().string(containsString("/loja/carros/" + id + "?origem=instagram")))
                .andExpect(content().string(containsString("Fiat Pulse 2023")));
        mvc.perform(get("/divulgacao/" + id + "/fotos.zip").with(admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));

        // marcar como publicado (link inválido é recusado)
        mvc.perform(post("/divulgacao/" + id + "/canais/olx").param("link", "olx.com/123").with(admin).with(csrf()))
                .andExpect(flash().attribute("erro", containsString("http")));
        mvc.perform(post("/divulgacao/" + id + "/canais/olx").param("link", "https://www.olx.com.br/123").with(admin).with(csrf()))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/divulgacao/" + id + "/canais/instagram").with(admin).with(csrf()))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(publicacoes.findByAnuncioId(id)).hasSize(2);
        mvc.perform(post("/divulgacao/" + id + "/canais/tiktok").with(admin).with(csrf())).andExpect(status().isNotFound());

        // visitante chega pelo link do Instagram, navega e manda contato: sai marcado com o canal
        MockHttpSession visitante = new MockHttpSession();
        mvc.perform(get("/loja/carros/" + id).param("origem", "instagram").session(visitante)).andExpect(status().isOk());
        mvc.perform(get("/").session(visitante)).andExpect(status().isOk());
        mvc.perform(post("/loja/carros/" + id + "/contato").session(visitante).with(csrf())
                        .param("tipo", "DUVIDA").param("nome", "Bruno Lima").param("telefone", "(19) 98888-1111"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(propostas.findByAnuncioIdOrderByCriadaEmDesc(id).get(0).getOrigem()).isEqualTo(CanalDivulgacao.INSTAGRAM);
        mvc.perform(get("/propostas").with(admin)).andExpect(content().string(containsString("via Instagram")));

        // vendeu: OLX e Instagram viram pendência até a equipe marcar que tirou do ar
        mvc.perform(post("/anuncios/" + id + "/venda").with(admin).with(csrf())
                        .param("comprador", "Bruno Lima").param("valor", "103.000")
                        .param("data", LocalDate.now().toString()).param("formaPagamento", "PIX"))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(get("/divulgacao").with(admin))
                .andExpect(content().string(containsString("Tire do ar")))
                .andExpect(content().string(containsString("Já tirei do OLX")));
        mvc.perform(post("/divulgacao/" + id + "/canais/olx/tirar").param("voltar", "lista").with(admin).with(csrf()))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/divulgacao/" + id + "/canais/instagram/tirar").param("voltar", "lista").with(admin).with(csrf()))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(get("/divulgacao").with(admin))
                .andExpect(content().string(not(containsString("Já tirei do OLX"))));
    }
}
