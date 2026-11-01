package br.com.dataflowauto.vendas;

import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.infra.LeitorXlsx;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import br.com.dataflowauto.vendas.infra.DespesaRepository;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo da loja de ponta a ponta: ficha → anúncio → loja → interessado → despesas → venda → relatórios. */
@SpringBootTest
class LojaIntegracaoTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private FichaService fichas;
    @Autowired
    private AnuncioRepository anuncios;
    @Autowired
    private DespesaRepository despesas;
    @Autowired
    private PropostaRepository propostas;
    @Autowired
    private LeitorXlsx leitorXlsx;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private long fichaConcluida(String placa) {
        FichaTecnica ficha = fichas.criar(new NovaFichaComando(placa, null, Map.of(
                CampoTecnico.MARCA, "Honda", CampoTecnico.MODELO, "Civic", CampoTecnico.ANO_MODELO, "2021",
                CampoTecnico.MOTORIZACAO, "2.0", CampoTecnico.COMBUSTIVEL, "Flex", CampoTecnico.CAMBIO, "CVT")));
        fichas.revisar(ficha.getId(), Map.of(), EnumSet.allOf(CampoTecnico.class), true);
        return ficha.getId();
    }

    @Test
    void painelExigeLoginELojaEPublica() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("ficha técnica conferida")))
                .andExpect(content().string(containsString("</html>")));
        mvc.perform(get("/entrar")).andExpect(status().isOk());
        for (String url : new String[]{"/painel", "/anuncios", "/propostas", "/planilhas", "/fichas", "/historico"}) {
            mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/entrar"));
        }
        // formulário sem token CSRF é recusado
        mvc.perform(post("/painel/demonstracao").with(user("admin"))).andExpect(status().isForbidden());
    }

    @Test
    void loginComUsuarioConfigurado() throws Exception {
        mvc.perform(post("/entrar").param("username", "admin").param("password", "dataflow123").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/painel"));
        mvc.perform(post("/entrar").param("username", "admin").param("password", "errada").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/entrar?error*"));
    }

    @Test
    void depoisDeCincoSenhasErradasNemASenhaCertaEntra() throws Exception {
        // IP próprio deste teste: o bloqueio não pode atrapalhar os outros logins do mesmo contexto
        RequestPostProcessor atacante = request -> {
            request.setRemoteAddr("203.0.113.7");
            return request;
        };
        for (int i = 1; i <= 4; i++) {
            mvc.perform(post("/entrar").param("username", "admin").param("password", "chute" + i).with(csrf()).with(atacante))
                    .andExpect(redirectedUrl("/entrar?error"));
        }
        mvc.perform(post("/entrar").param("username", "admin").param("password", "chute5").with(csrf()).with(atacante))
                .andExpect(redirectedUrl("/entrar?bloqueado"));
        mvc.perform(post("/entrar").param("username", "admin").param("password", "dataflow123").with(csrf()).with(atacante))
                .andExpect(redirectedUrl("/entrar?bloqueado"));

        // outro IP com a senha certa continua entrando
        mvc.perform(post("/entrar").param("username", "admin").param("password", "dataflow123").with(csrf()))
                .andExpect(redirectedUrl("/painel"));
        mvc.perform(get("/entrar").param("bloqueado", ""))
                .andExpect(content().string(containsString("Muitas tentativas erradas")));
    }

    @Test
    void fluxoCompletoDaLoja() throws Exception {
        long fichaId = fichaConcluida("QWE4R56");
        var admin = user("admin").roles("ADMIN");

        // anunciar com foto
        MvcResult criado = mvc.perform(multipart("/anuncios")
                        .file(new MockMultipartFile("fotos", "frente.png", "image/png", PNG))
                        .param("fichaId", String.valueOf(fichaId))
                        .param("preco", "R$ 118.900,00").param("quilometragem", "35.000")
                        .param("valorCompra", "100.000").param("dataCompra", LocalDate.now().minusDays(20).toString())
                        .param("descricao", "Revisado").with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String destino = criado.getResponse().getRedirectedUrl();
        long id = Long.parseLong(destino.substring(destino.lastIndexOf('/') + 1));
        var anuncio = anuncios.findById(id).orElseThrow();
        assertThat(anuncio.getTitulo()).isEqualTo("Honda Civic");
        assertThat(anuncio.getFotos()).hasSize(1);

        // a ficha não pode ser excluída enquanto houver anúncio
        mvc.perform(post("/fichas/" + fichaId + "/excluir").with(admin).with(csrf()).header("Referer", "http://localhost/fichas"))
                .andExpect(flash().attribute("erro", containsString("anúncio")));

        // aparece na loja, com filtro, página do carro, simulação e foto pública
        mvc.perform(get("/").param("busca", "civic")).andExpect(content().string(containsString("Honda Civic")));
        // outros testes (como a demonstração) também anunciam Civics, então a checagem é pelo link deste carro
        mvc.perform(get("/").param("cambio", "Manual")).andExpect(content().string(not(containsString("/loja/carros/" + id + "\""))));
        mvc.perform(get("/loja/carros/" + id).param("entrada", "18.900").param("parcelas", "48"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 100.000,00")))
                .andExpect(content().string(containsString("48x de")))
                // a página precisa ser renderizada até o fim (o formulário de contato usa token CSRF)
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("</html>")));
        mvc.perform(get("/fotos/" + anuncio.getFotos().get(0))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        byte[] pdfPublico = mvc.perform(get("/loja/carros/" + id + "/ficha-tecnica.pdf")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        // o PDF público segue a mesma regra da página: placa só pelo final, sem lista de fontes (nomes de arquivos)
        try (PDDocument pdf = Loader.loadPDF(pdfPublico)) {
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto).contains("Final da placa 6").doesNotContain("QWE4R56").doesNotContain("QWE-4R56")
                    .doesNotContain("Fontes consultadas");
            assertThat(pdf.getDocumentInformation().getTitle()).doesNotContain("QWE4R56");
        }

        // visitante faz uma proposta sem login
        mvc.perform(post("/loja/carros/" + id + "/contato").with(csrf())
                        .param("tipo", "PROPOSTA").param("nome", "Paula Reis").param("telefone", "(19) 98888-7777")
                        .param("valorOferecido", "112.000"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/loja/carros/" + id + "/contato").with(csrf()).param("tipo", "DUVIDA").param("nome", "Sem contato"))
                .andExpect(flash().attribute("erro", containsString("telefone ou e-mail")));
        long propostaId = propostas.findByAnuncioIdOrderByCriadaEmDesc(id).get(0).getId();
        mvc.perform(get("/propostas").with(admin)).andExpect(content().string(containsString("Paula Reis")));

        // despesa manual e importação de planilha (uma linha com placa fora do estoque)
        mvc.perform(post("/anuncios/" + id + "/despesas").with(admin).with(csrf())
                        .param("data", LocalDate.now().toString()).param("categoria", "MECANICA")
                        .param("descricao", "Troca de pastilhas").param("valor", "450,00"))
                .andExpect(status().is3xxRedirection());
        String hoje = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String csv = "Data;Placa;Categoria;Descrição;Valor\n"
                + hoje + ";QWE-4R56;Lavagem;Higienização;R$ 150,00\n"
                + hoje + ";AAA1111;Pneus;Pneu;300,00\n";
        mvc.perform(multipart("/anuncios/despesas/importar")
                        .file(new MockMultipartFile("arquivo", "despesas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(admin).with(csrf()))
                .andExpect(flash().attribute("sucesso", containsString("1 despesa")))
                .andExpect(flash().attribute("problemasImportacao", org.hamcrest.Matchers.hasSize(1)));
        assertThat(despesas.findByAnuncioIdOrderByDataDescIdDesc(id)).hasSize(2)
                .anyMatch(d -> d.getOrigem().startsWith("Planilha: despesas.csv"));

        // reenviar a mesma planilha não duplica o custo: a linha já lançada é ignorada e explicada
        mvc.perform(multipart("/anuncios/despesas/importar")
                        .file(new MockMultipartFile("arquivo", "despesas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(admin).with(csrf()))
                .andExpect(flash().attribute("erro", "Nenhuma despesa importada."))
                .andExpect(flash().attribute("problemasImportacao",
                        org.hamcrest.Matchers.hasItem(containsString("já estava lançada"))));
        assertThat(despesas.findByAnuncioIdOrderByDataDescIdDesc(id)).hasSize(2);

        // custo total = 100.000 + 450 + 150; lucro previsto = 118.900 - 100.600
        mvc.perform(get("/anuncios/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 100.600,00")))
                .andExpect(content().string(containsString("R$ 18.300,00")));

        // venda a partir da proposta: carro sai da loja e o interessado vira venda
        mvc.perform(post("/anuncios/" + id + "/venda").with(admin).with(csrf())
                        .param("comprador", "Paula Reis").param("documento", "529.982.247-25").param("valor", "115.000")
                        .param("data", LocalDate.now().toString()).param("formaPagamento", "FINANCIAMENTO")
                        .param("propostaId", String.valueOf(propostaId)))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(anuncios.findById(id).orElseThrow().getStatus()).isEqualTo(StatusAnuncio.VENDIDO);
        assertThat(propostas.findById(propostaId).orElseThrow().getStatus()).isEqualTo(StatusProposta.CONVERTIDA);
        mvc.perform(get("/loja/carros/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(content().string(not(containsString("/loja/carros/" + id + "\""))));

        // painel e relatórios
        mvc.perform(get("/painel").with(admin)).andExpect(status().isOk()).andExpect(content().string(containsString("Paula Reis")));
        MvcResult vendas = mvc.perform(get("/painel/exportar/vendas").with(admin)).andExpect(status().isOk()).andReturn();
        Planilha planilha = leitorXlsx.ler(new ByteArrayInputStream(vendas.getResponse().getContentAsByteArray()), "vendas");
        assertThat(planilha.getLinhas()).anyMatch(l -> l.getValor("Comprador").equals("Paula Reis")
                && l.getValor("Lucro").equals("14400,00"));
        mvc.perform(get("/painel/exportar/estoque").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/painel/exportar/despesas").with(admin)).andExpect(status().isOk());
    }

    @Test
    void demonstracaoMontaLojaDeExemplo() throws Exception {
        var admin = user("admin").roles("ADMIN");
        mvc.perform(post("/painel/demonstracao").with(admin).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/painel/demonstracao").with(admin).with(csrf()))
                .andExpect(flash().attribute("sucesso", containsString("já estavam")));
        mvc.perform(get("/")).andExpect(content().string(containsString("Volkswagen Polo")))
                .andExpect(content().string(containsString("Fiat Strada")));
        mvc.perform(get("/painel").with(admin)).andExpect(status().isOk());

        // procedência aparece para o cliente; proprietários, multas e débitos ficam só na equipe
        var polo = anuncios.findByFichaVeiculoPlacaOrderByCriadoEmDesc("ABC1D23").get(0);
        mvc.perform(get("/loja/carros/" + polo.getId()))
                .andExpect(content().string(containsString("Procedência e histórico")))
                .andExpect(content().string(containsString("Roubo ou furto")))
                .andExpect(content().string(not(containsString("Proprietário atual"))))
                .andExpect(content().string(not(containsString("Carla Regina Souza"))))
                .andExpect(content().string(not(containsString("RENAVAM"))));
        mvc.perform(get("/fichas/" + polo.getFicha().getId()).with(admin))
                .andExpect(content().string(containsString("Carla Regina Souza")));

        // 7 lugares: os dois tamanhos do porta-malas na mesma linha; carro de 5 lugares não ganha a segunda medida
        var spin = anuncios.findByFichaVeiculoPlacaOrderByCriadoEmDesc("SPN6C42").get(0);
        mvc.perform(get("/loja/carros/" + spin.getId()))
                .andExpect(content().string(containsString("553 L com 5 lugares")))
                .andExpect(content().string(containsString("162 L com 7 lugares")))
                .andExpect(content().string(not(containsString("<dt>Porta-malas com 7 lugares</dt>"))));
        var corolla = anuncios.findByFichaVeiculoPlacaOrderByCriadoEmDesc("RTC4B21").get(0);
        mvc.perform(get("/loja/carros/" + corolla.getId()))
                .andExpect(content().string(containsString("470 L")))
                .andExpect(content().string(not(containsString("com 7 lugares"))));
    }

    @Test
    void soAPlacaPreencheFichaComProcedenciaEConsultaPodeSerRefeita() throws Exception {
        var admin = user("admin").roles("ADMIN");
        MvcResult criada = mvc.perform(multipart("/fichas").param("placa", "CMD2B34").with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String destino = criada.getResponse().getRedirectedUrl();
        mvc.perform(get(destino).with(admin))
                .andExpect(content().string(containsString("Commander")))
                .andExpect(content().string(containsString("Otávio Nascimento")))
                .andExpect(content().string(containsString("Alienação fiduciária ATIVA")))
                // 7 lugares: o porta-malas com todos os bancos fica dentro do quadro do porta-malas
                .andExpect(content().string(containsString("Com 7 lugares")))
                .andExpect(content().string(containsString("value=\"233 L\"")))
                .andExpect(content().string(not(containsString("<span>Porta-malas com 7 lugares</span>"))));
        mvc.perform(post(destino + "/consultar").with(admin).with(csrf()))
                .andExpect(flash().attribute("sucesso", containsString("nenhuma informação nova")));
    }

    @Test
    void fotoInvalidaERecusada() throws Exception {
        long fichaId = fichaConcluida("JKL7M89");
        mvc.perform(multipart("/anuncios")
                        .file(new MockMultipartFile("fotos", "virus.png", "image/png", "nao sou imagem".getBytes()))
                        .with(user("admin")).with(csrf())
                        .param("fichaId", String.valueOf(fichaId)).param("preco", "50000").param("quilometragem", "10")
                        .header("Referer", "http://localhost/anuncios/novo"))
                .andExpect(flash().attribute("erro", containsString("não é uma imagem")));
        mvc.perform(get("/fotos/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
    }
}
