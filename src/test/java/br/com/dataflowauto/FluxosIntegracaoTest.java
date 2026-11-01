package br.com.dataflowauto;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.fichas.dominio.StatusVerificacao;
import br.com.dataflowauto.fichas.infra.FichaTecnicaRepository;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.infra.LeitorXlsx;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Testa os fluxos completos pela camada web, com banco H2 em memória, como usuário logado no painel. */
@SpringBootTest
class FluxosIntegracaoTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void configurarMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                .build();
    }

    @Autowired
    private FichaTecnicaRepository fichas;

    private static MockMultipartFile csv(String nome, String conteudo) {
        return new MockMultipartFile("arquivos", nome, "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private long importar(MockMultipartFile arquivo) throws Exception {
        MvcResult r = mvc.perform(multipart("/planilhas/importar").file(arquivo))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String destino = r.getResponse().getRedirectedUrl();
        return Long.parseLong(destino.substring(destino.lastIndexOf('/') + 1));
    }

    @Test
    void importarOrganizarCombinarEExportarPlanilha() throws Exception {
        long clientes = importar(csv("clientes.csv", """
                Nome;CPF;Cidade
                ana silva;123.456.789-00;Campinas
                Bruno Lima;98765432100;Sumaré
                ana silva;12345678900;Campinas
                """));
        long veiculos = importar(csv("veiculos.csv", """
                cpf_dono,placa,modelo
                12345678900,ABC1D23,Polo
                98765432100,BRA2E19,Strada
                """));

        mvc.perform(get("/planilhas/" + clientes))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bruno Lima")));

        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(post("/planilhas/" + clientes + "/editar").session(sessao))
                .andExpect(status().is3xxRedirection());
        String area = areaDaPagina(sessao);

        // padroniza CPF e nome, remove duplicatas, tira a coluna Cidade e renomeia Nome
        mvc.perform(post("/planilhas/trabalho/organizar").session(sessao).param("area", area)
                        .param("colunas[0].nome", "Nome").param("colunas[0].incluir", "true")
                        .param("colunas[0].ordem", "2").param("colunas[0].novoNome", "Cliente")
                        .param("colunas[0].padronizacao", "NOME_PROPRIO")
                        .param("colunas[1].nome", "CPF").param("colunas[1].incluir", "true")
                        .param("colunas[1].ordem", "1").param("colunas[1].padronizacao", "CPF")
                        .param("colunas[2].nome", "Cidade").param("colunas[2].ordem", "3")
                        .param("removerDuplicatas", "true").param("duplicataPorColuna", "CPF"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));

        mvc.perform(post("/planilhas/trabalho/combinar").session(sessao).param("area", area)
                        .param("arquivoId", String.valueOf(veiculos))
                        .param("chaveAtual", "CPF").param("chaveOutra", "cpf_dono").param("tipo", "ESQUERDA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));

        MvcResult exportado = mvc.perform(get("/planilhas/trabalho/exportar").param("formato", "xlsx")
                        .param("area", area).session(sessao))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        Planilha resultado = new LeitorXlsx().ler(
                new ByteArrayInputStream(exportado.getResponse().getContentAsByteArray()), "resultado");

        assertThat(resultado.getNomesColunas()).containsExactly("CPF", "Cliente", "placa", "modelo");
        assertThat(resultado.totalLinhas()).isEqualTo(2);
        assertThat(resultado.getLinhas().get(0).getValores())
                .containsEntry("CPF", "123.456.789-00").containsEntry("Cliente", "Ana Silva")
                .containsEntry("placa", "ABC1D23");

        // desfazer volta para antes da combinação
        mvc.perform(post("/planilhas/trabalho/desfazer").session(sessao).param("area", area))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));
        MvcResult csvExportado = mvc.perform(get("/planilhas/trabalho/exportar").param("formato", "csv")
                        .param("area", area).session(sessao))
                .andExpect(status().isOk()).andReturn();
        assertThat(csvExportado.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("CPF;Cliente\r\n");
    }

    /** Identificador da planilha aberta, como vai nos formulários da área de trabalho. */
    private String areaDaPagina(MockHttpSession sessao) throws Exception {
        MvcResult pagina = mvc.perform(get("/planilhas/trabalho").session(sessao)).andExpect(status().isOk()).andReturn();
        return (String) pagina.getModelAndView().getModel().get("area");
    }

    @Test
    void abaAntigaNaoAlteraNemExportaAPlanilhaAbertaEmOutraAba() throws Exception {
        long clientes = importar(csv("clientes-abas.csv", "Nome;CPF\nAna;1\nBia;2\n"));
        long veiculos = importar(csv("veiculos-abas.csv", "placa;modelo\nABC1D23;Polo\n"));
        MockHttpSession sessao = new MockHttpSession();

        // aba 1 abre clientes; depois a aba 2, no mesmo navegador, abre veículos
        mvc.perform(post("/planilhas/" + clientes + "/editar").session(sessao));
        String areaAba1 = areaDaPagina(sessao);
        mvc.perform(post("/planilhas/" + veiculos + "/editar").session(sessao));

        // a aba 1 tenta tirar uma coluna e exportar: nada acontece com a planilha de veículos
        mvc.perform(post("/planilhas/trabalho/organizar").session(sessao).param("area", areaAba1)
                        .header("Referer", "http://localhost/planilhas/trabalho")
                        .param("colunas[0].nome", "placa").param("colunas[0].incluir", "true").param("colunas[0].ordem", "1")
                        .param("colunas[1].nome", "modelo").param("colunas[1].ordem", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("outra aba")));
        mvc.perform(get("/planilhas/trabalho/exportar").param("formato", "csv").param("area", areaAba1).session(sessao))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("outra aba")));

        String areaAba2 = areaDaPagina(sessao);
        MvcResult exportado = mvc.perform(get("/planilhas/trabalho/exportar").param("formato", "csv")
                        .param("area", areaAba2).session(sessao))
                .andExpect(status().isOk()).andReturn();
        assertThat(exportado.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("placa;modelo");
    }

    @Test
    void arquivoComExtensaoNaoPermitidaEhRecusadoComMensagem() throws Exception {
        mvc.perform(multipart("/planilhas/importar")
                        .file(new MockMultipartFile("arquivos", "virus.exe", "application/octet-stream", new byte[]{1, 2}))
                        .header("Referer", "http://localhost/planilhas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/planilhas*"))
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("Formato não aceito")));
    }

    @Test
    void xlsxFalsoEhRecusado() throws Exception {
        mvc.perform(multipart("/planilhas/importar")
                        .file(new MockMultipartFile("arquivos", "falso.xlsx", "application/octet-stream",
                                "texto".getBytes())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("não corresponde")));
    }

    @Test
    void criarFichaComDocumentoRevisarConcluirEGerarPdf() throws Exception {
        MockMultipartFile documento = new MockMultipartFile("documento", "crlv.pdf", "application/pdf",
                pdfCom("MARCA/MODELO/VERSÃO: VW/POLO HIGHLINE", "ANO FAB/MOD: 2023/2024",
                        "Combustível: Gasolina", "Câmbio: Automático"));

        MvcResult criada = mvc.perform(multipart("/fichas").file(documento).param("placa", "abc-1d23"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String url = criada.getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));

        FichaTecnica ficha = fichas.findById(id).orElseThrow();
        // documento tem prioridade sobre a base de demonstração
        assertThat(ficha.campo(CampoTecnico.MODELO).getValor()).isEqualTo("POLO HIGHLINE");
        assertThat(ficha.campo(CampoTecnico.MODELO).getOrigem()).isEqualTo(OrigemDado.DOCUMENTO);
        assertThat(ficha.campo(CampoTecnico.COMBUSTIVEL).getValor()).isEqualTo("Gasolina");
        // o que o documento não trouxe veio da base de demonstração
        assertThat(ficha.campo(CampoTecnico.MOTORIZACAO).getOrigem()).isEqualTo(OrigemDado.FONTE_EXTERNA);
        assertThat(ficha.campo(CampoTecnico.MOTORIZACAO).getStatus()).isEqualTo(StatusVerificacao.A_CONFIRMAR);
        assertThat(ficha.getArquivoOrigem()).isNotNull();

        mvc.perform(get("/fichas/" + id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("POLO HIGHLINE")));

        // tentar concluir sem confirmar: salva, mas avisa
        mvc.perform(post("/fichas/" + id).param("acao", "concluir").param("valores[COMBUSTIVEL]", "Flex"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("pendentes")));
        ficha = fichas.findById(id).orElseThrow();
        assertThat(ficha.campo(CampoTecnico.COMBUSTIVEL).getValor()).isEqualTo("Flex");
        assertThat(ficha.campo(CampoTecnico.COMBUSTIVEL).getOrigem()).isEqualTo(OrigemDado.USUARIO);

        var confirmar = post("/fichas/" + id).param("acao", "concluir");
        for (var campo : ficha.getCampos()) {
            if (campo.getStatus() == StatusVerificacao.A_CONFIRMAR) {
                confirmar.param("confirmados", campo.getCampo().name());
            }
        }
        mvc.perform(confirmar).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));
        assertThat(fichas.findById(id).orElseThrow().getStatus()).isEqualTo(StatusFicha.CONCLUIDA);

        MvcResult pdf = mvc.perform(get("/fichas/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void placaInvalidaMostraErro() throws Exception {
        mvc.perform(multipart("/fichas").param("placa", "123").header("Referer", "http://localhost/fichas/nova"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/fichas/nova*"))
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("Placa inválida")));
    }

    @Test
    void fichaInexistenteDa404() throws Exception {
        mvc.perform(get("/fichas/999999")).andExpect(status().isNotFound());
    }

    @Test
    void paginasPrincipaisAbrem() throws Exception {
        for (String url : new String[]{"/", "/painel", "/planilhas", "/fichas", "/fichas/nova", "/historico",
                "/anuncios", "/anuncios/novo", "/propostas"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    private static byte[] pdfCom(String... linhas) throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, saida);
        doc.open();
        for (String linha : linhas) {
            doc.add(new Paragraph(linha));
        }
        doc.close();
        return saida.toByteArray();
    }
}
