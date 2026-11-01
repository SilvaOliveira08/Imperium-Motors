package br.com.dataflowauto.fichas;

import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.FonteDadosVeiculo;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/**
 * Consulta às fontes de dados com a API de placa configurada mas fora do ar (porta 1 recusa a conexão)
 * e com uma fonte espiã que anota se foi chamada dentro de uma transação.
 */
// sem trocar spring.datasource.url aqui: propriedades do teste venceriam o perfil Maven "postgres"
@SpringBootTest(properties = "dataflow.fichas.api-placa.url=http://localhost:1")
class FichaFontesIntegracaoTest {

    private static final String PLACA_ESPIA = "TRX0A00";

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private FichaService fichas;
    @Autowired
    private FonteEspia espia;

    @Test
    void falhaDaApiFicaRegistradaNaFichaEAvisadaNaTela() throws Exception {
        FichaTecnica ficha = fichas.criar(new NovaFichaComando("QWE1R23", null, Map.of(CampoTecnico.MARCA, "Fiat")));

        assertThat(ficha.consultaIncompleta()).isTrue();
        assertThat(ficha.getFontesConsultadas())
                .contains("API de consulta por placa (indisponível")
                .doesNotContain("0 encontrados");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        mvc.perform(post("/fichas/" + ficha.getId() + "/consultar").with(user("admin")).with(csrf()))
                .andExpect(flash().attribute("erro", containsString("não respondeu")))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void fontesSaoConsultadasForaDaTransacao() {
        espia.chamadasEmTransacao.clear();

        FichaTecnica ficha = fichas.criar(new NovaFichaComando(PLACA_ESPIA, null, Map.of()));
        int novos = fichas.reconsultar(ficha.getId());

        // uma chamada na criação e outra na nova consulta, nenhuma com transação aberta
        assertThat(espia.chamadasEmTransacao).containsExactly(false, false);
        assertThat(ficha.campo(CampoTecnico.COR).getValor()).isEqualTo("Prata");
        assertThat(novos).isZero();
    }

    /** Fonte de teste que só atende a placa {@link #PLACA_ESPIA}. */
    static class FonteEspia implements FonteDadosVeiculo {

        final List<Boolean> chamadasEmTransacao = new CopyOnWriteArrayList<>();

        @Override
        public String nome() {
            return "Fonte espiã";
        }

        @Override
        public OrigemDado origem() {
            return OrigemDado.FONTE_EXTERNA;
        }

        @Override
        public boolean podeAtender(ConsultaVeiculo consulta) {
            return PLACA_ESPIA.equals(consulta.placa().valor());
        }

        @Override
        public Map<CampoTecnico, String> buscar(ConsultaVeiculo consulta) {
            chamadasEmTransacao.add(TransactionSynchronizationManager.isActualTransactionActive());
            return Map.of(CampoTecnico.COR, "Prata");
        }
    }

    @TestConfiguration
    static class ConfiguracaoFonteEspia {

        @Bean
        FonteEspia fonteEspia() {
            return new FonteEspia();
        }
    }
}
