package br.com.dataflowauto.vendas;

import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.vendas.aplicacao.AnuncioService;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A6: a página inicial da loja não pode fazer mais consultas ao banco conforme o estoque cresce. */
@SpringBootTest
class CatalogoConsultasTest {

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private FichaService fichas;
    @Autowired
    private AnuncioService anuncios;

    private void anunciar(String placa, String marca) {
        FichaTecnica ficha = fichas.criar(new NovaFichaComando(placa, null, Map.of(
                CampoTecnico.MARCA, marca, CampoTecnico.MODELO, "Modelo", CampoTecnico.ANO_MODELO, "2022",
                CampoTecnico.MOTORIZACAO, "1.0", CampoTecnico.COMBUSTIVEL, "Flex", CampoTecnico.CAMBIO, "Manual")));
        fichas.revisar(ficha.getId(), Map.of(), EnumSet.allOf(CampoTecnico.class), true);
        anuncios.criar(ficha.getId(), new DadosAnuncio(null, new BigDecimal("55000"), 30000, null, null, null, true), List.of());
    }

    private long consultasDaPaginaInicial(MockMvc mvc, Statistics estatisticas) throws Exception {
        estatisticas.clear();
        mvc.perform(get("/")).andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    @Test
    void paginaInicialFazPoucasConsultasEOMesmoNumeroComMaisCarros() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
        try {
            anunciar("CAT1A11", "Fiat");
            anunciar("CAT2B22", "Renault");
            long comPoucos = consultasDaPaginaInicial(mvc, estatisticas);

            anunciar("CAT3C33", "Peugeot");
            anunciar("CAT4D44", "Citroën");
            anunciar("CAT5E55", "Jeep");
            long comMais = consultasDaPaginaInicial(mvc, estatisticas);

            // antes do A6 eram 15 consultas com 2 carros e 33 com 5 (6 a mais por carro); hoje são 3
            assertThat(comMais).isEqualTo(comPoucos);
            assertThat(comMais).isLessThanOrEqualTo(5);
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }
}
