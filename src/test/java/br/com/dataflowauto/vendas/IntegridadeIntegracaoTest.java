package br.com.dataflowauto.vendas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.vendas.aplicacao.AnuncioService;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.Venda;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Um anúncio em andamento por carro e nenhuma alteração simultânea sobrescrevendo outra (A5). */
@SpringBootTest
class IntegridadeIntegracaoTest {

    @Autowired
    private FichaService fichas;
    @Autowired
    private AnuncioService anuncios;
    @Autowired
    private AnuncioRepository repositorio;

    private FichaTecnica fichaConcluida(String placa) {
        FichaTecnica ficha = fichas.criar(new NovaFichaComando(placa, null, Map.of(
                CampoTecnico.MARCA, "Honda", CampoTecnico.MODELO, "Fit", CampoTecnico.ANO_MODELO, "2020",
                CampoTecnico.MOTORIZACAO, "1.5", CampoTecnico.COMBUSTIVEL, "Flex", CampoTecnico.CAMBIO, "CVT")));
        return fichas.revisar(ficha.getId(), Map.of(), EnumSet.allOf(CampoTecnico.class), true);
    }

    private static DadosAnuncio dados() {
        return new DadosAnuncio(null, new BigDecimal("75000"), 40000, new BigDecimal("60000"), null, null, false);
    }

    @Test
    void mesmaPlacaComOutraFichaNaoPodeSerAnunciadaDuasVezes() {
        FichaTecnica primeira = fichaConcluida("INT1A11");
        FichaTecnica segunda = fichaConcluida("INT1A11");
        anuncios.criar(primeira.getId(), dados(), List.of());

        assertThatThrownBy(() -> anuncios.criar(segunda.getId(), dados(), List.of()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já tem um anúncio em andamento");
        assertThat(anuncios.fichasProntasParaAnunciar()).extracting(FichaTecnica::getId)
                .doesNotContain(primeira.getId(), segunda.getId());
    }

    @Test
    void bancoRecusaSegundoAnuncioEmAndamentoMesmoSemPassarPeloServico() {
        // simula o clique duplo: as duas requisições passaram pela checagem antes de qualquer uma gravar
        FichaTecnica ficha = fichaConcluida("INT2B22");
        repositorio.saveAndFlush(new Anuncio(ficha, dados()));

        assertThatThrownBy(() -> repositorio.saveAndFlush(new Anuncio(ficha, dados())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void depoisDaVendaOCarroPodeSerAnunciadoDeNovoMasAVendaAntigaNaoPodeSerDesfeita() {
        FichaTecnica ficha = fichaConcluida("INT3C33");
        Anuncio vendido = anuncios.criar(ficha.getId(), dados(), List.of());
        anuncios.registrarVenda(vendido.getId(),
                new Venda("Ana", null, new BigDecimal("74000"), LocalDate.now(), FormaPagamento.PIX), null);

        // a loja recomprou o carro e anunciou de novo
        Anuncio novo = anuncios.criar(ficha.getId(), dados(), List.of());
        assertThat(novo.getStatus()).isEqualTo(StatusAnuncio.DISPONIVEL);

        assertThatThrownBy(() -> anuncios.desfazerVenda(vendido.getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi anunciada de novo");
        assertThat(anuncios.buscar(vendido.getId()).estaVendido()).isTrue();
    }

    @Test
    void duasPessoasAlterandoOMesmoAnuncioAoMesmoTempoNaoSobrescrevemUmaAOutra() {
        FichaTecnica ficha = fichaConcluida("INT4D44");
        Long id = anuncios.criar(ficha.getId(), dados(), List.of()).getId();

        // as duas abriram o anúncio na mesma versão
        Anuncio daPrimeira = repositorio.findById(id).orElseThrow();
        Anuncio daSegunda = repositorio.findById(id).orElseThrow();

        daPrimeira.alterarStatus(StatusAnuncio.OCULTO);
        repositorio.save(daPrimeira);

        daSegunda.registrarVenda(new Venda("Bia", null, new BigDecimal("74000"), LocalDate.now(), FormaPagamento.PIX));
        assertThatThrownBy(() -> repositorio.save(daSegunda))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // ficou o que a primeira gravou, sem dados de venda misturados
        Anuncio gravado = anuncios.buscar(id);
        assertThat(gravado.getStatus()).isEqualTo(StatusAnuncio.OCULTO);
        assertThat(gravado.getVenda()).isNull();
    }
}
