package br.com.dataflowauto.vendas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.CategoriaDespesa;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.Despesa;
import br.com.dataflowauto.vendas.dominio.Dinheiro;
import br.com.dataflowauto.vendas.dominio.FiltroLoja;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.NovaProposta;
import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.dominio.Rentabilidade;
import br.com.dataflowauto.vendas.dominio.SimulacaoFinanciamento;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.TipoProposta;
import br.com.dataflowauto.vendas.dominio.Venda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VendasDominioTest {

    private static FichaTecnica fichaConcluida(String placa, Map<CampoTecnico, String> dados) {
        FichaTecnica ficha = new FichaTecnica(new Veiculo(new Placa(placa)));
        dados.forEach((campo, valor) -> ficha.editar(campo, valor));
        ficha.concluir();
        return ficha;
    }

    private static FichaTecnica polo() {
        return fichaConcluida("ABC1D23", Map.of(CampoTecnico.MARCA, "Volkswagen", CampoTecnico.MODELO, "Polo",
                CampoTecnico.VERSAO, "Highline", CampoTecnico.ANO_FABRICACAO, "2023", CampoTecnico.ANO_MODELO, "2024",
                CampoTecnico.MOTORIZACAO, "1.0 TSI", CampoTecnico.COMBUSTIVEL, "Flex",
                CampoTecnico.CAMBIO, "Automático de 6 marchas"));
    }

    private static DadosAnuncio dados(String preco, int km) {
        return new DadosAnuncio(null, new BigDecimal(preco), km, new BigDecimal("80000"), LocalDate.now().minusDays(30), null, false);
    }

    @Test
    void anuncioUsaDadosConfirmadosDaFicha() {
        Anuncio anuncio = new Anuncio(polo(), dados("99900", 20000));
        assertThat(anuncio.getTitulo()).isEqualTo("Volkswagen Polo Highline");
        assertThat(anuncio.getAnos()).isEqualTo("2023/2024");
        assertThat(anuncio.getTipoCambio()).isEqualTo("Automático");
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.DISPONIVEL);
        assertThat(anuncio.estaNaLoja()).isTrue();
    }

    @Test
    void fichaNaoConcluidaNaoViraAnuncio() {
        FichaTecnica ficha = new FichaTecnica(new Veiculo(new Placa("ABC1D23")));
        ficha.registrarDado(CampoTecnico.MARCA, "Fiat", OrigemDado.FONTE_EXTERNA, "teste");
        assertThatThrownBy(() -> new Anuncio(ficha, dados("50000", 1000)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Conclua a ficha");
    }

    @Test
    void anuncioSaiDaLojaQuandoFichaVoltaParaRevisao() {
        FichaTecnica ficha = polo();
        Anuncio anuncio = new Anuncio(ficha, dados("99900", 20000));
        ficha.registrarDado(CampoTecnico.COR, "Prata", OrigemDado.DOCUMENTO, "CRLV");
        assertThat(anuncio.estaNaLoja()).isFalse();
        assertThatThrownBy(() -> anuncio.alterarStatus(StatusAnuncio.DISPONIVEL)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void precoEKmSaoObrigatorios() {
        assertThatThrownBy(() -> new Anuncio(polo(), dados("0", 1000))).hasMessageContaining("preço");
        assertThatThrownBy(() -> new Anuncio(polo(), new DadosAnuncio(null, BigDecimal.TEN, -1, null, null, null, false)))
                .hasMessageContaining("quilometragem");
    }

    @Test
    void vendaFechaAnuncioENaoPodeSerRepetida() {
        Anuncio anuncio = new Anuncio(polo(), dados("99900", 20000));
        anuncio.registrarVenda(new Venda("Ana", "123.456.789-09", new BigDecimal("97000"), LocalDate.now(), FormaPagamento.PIX));
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.VENDIDO);
        assertThat(anuncio.estaNaLoja()).isFalse();
        assertThat(anuncio.getVenda().getDocumentoFormatado()).isEqualTo("123.456.789-09");
        assertThatThrownBy(() -> anuncio.registrarVenda(new Venda("Bia", null, BigDecimal.ONE, LocalDate.now(), FormaPagamento.PIX)))
                .hasMessageContaining("já foi vendido");
        assertThatThrownBy(() -> anuncio.alterarStatus(StatusAnuncio.DISPONIVEL)).isInstanceOf(RegraNegocioException.class);
        anuncio.desfazerVenda();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.OCULTO);
        assertThat(anuncio.getVenda()).isNull();
    }

    @Test
    void vendaValidaDocumentoEData() {
        assertThatThrownBy(() -> new Venda("Ana", "123", BigDecimal.TEN, LocalDate.now(), FormaPagamento.PIX))
                .hasMessageContaining("CPF");
        assertThatThrownBy(() -> new Venda("Ana", null, BigDecimal.TEN, LocalDate.now().plusDays(1), FormaPagamento.PIX))
                .hasMessageContaining("futuro");
        Anuncio anuncio = new Anuncio(polo(), dados("99900", 20000));
        assertThatThrownBy(() -> anuncio.registrarVenda(new Venda("Ana", null, BigDecimal.TEN, LocalDate.now().minusDays(60), FormaPagamento.PIX)))
                .hasMessageContaining("anterior à data de compra");
    }

    @Test
    void rentabilidadePrevistaERealizada() {
        Anuncio anuncio = new Anuncio(polo(), dados("100000", 20000));
        List<Despesa> gastos = List.of(
                new Despesa(anuncio, LocalDate.now(), CategoriaDespesa.PNEUS, "Pneus", new BigDecimal("2000"), null),
                new Despesa(anuncio, LocalDate.now(), CategoriaDespesa.ESTETICA, null, new BigDecimal("500"), null));

        Rentabilidade prevista = Rentabilidade.calcular(anuncio, gastos);
        assertThat(prevista.custoTotal()).isEqualByComparingTo("82500");
        assertThat(prevista.lucro()).isEqualByComparingTo("17500");
        assertThat(prevista.margemPercentual()).isEqualByComparingTo("17.5");
        assertThat(prevista.realizada()).isFalse();

        anuncio.registrarVenda(new Venda("Ana", null, new BigDecimal("80000"), LocalDate.now(), FormaPagamento.A_VISTA));
        Rentabilidade realizada = Rentabilidade.calcular(anuncio, gastos);
        assertThat(realizada.realizada()).isTrue();
        assertThat(realizada.lucro()).isEqualByComparingTo("-2500");
        assertThat(realizada.prejuizo()).isTrue();
    }

    @Test
    void mesmaDespesaTemAMesmaChaveIndependenteDaOrigem() {
        Anuncio anuncio = new Anuncio(polo(), dados("100000", 20000));
        LocalDate dia = LocalDate.now().minusDays(3);
        Despesa daPlanilha = new Despesa(anuncio, dia, CategoriaDespesa.PNEUS, "Pneu dianteiro", new BigDecimal("300"), "Planilha: a.xlsx, linha 2");
        Despesa reimportada = new Despesa(anuncio, dia, CategoriaDespesa.PNEUS, " pneu DIANTEIRO ", new BigDecimal("300.00"), "Planilha: a.xlsx, linha 5");
        Despesa outroValor = new Despesa(anuncio, dia, CategoriaDespesa.PNEUS, "Pneu dianteiro", new BigDecimal("310"), null);
        Despesa outroDia = new Despesa(anuncio, dia.minusDays(1), CategoriaDespesa.PNEUS, "Pneu dianteiro", new BigDecimal("300"), null);

        assertThat(reimportada.chaveLancamento()).isEqualTo(daPlanilha.chaveLancamento());
        assertThat(outroValor.chaveLancamento()).isNotEqualTo(daPlanilha.chaveLancamento());
        assertThat(outroDia.chaveLancamento()).isNotEqualTo(daPlanilha.chaveLancamento());
    }

    @Test
    void simulacaoPelaTabelaPrice() {
        SimulacaoFinanciamento s = SimulacaoFinanciamento.calcular(new BigDecimal("100000"), new BigDecimal("40000"), 48, new BigDecimal("1.5"));
        assertThat(s.valorFinanciado()).isEqualByComparingTo("60000");
        // 60000 * 0,015 / (1 - 1,015^-48) = 1762,50
        assertThat(s.valorParcela()).isEqualByComparingTo("1762.50");
        assertThat(s.totalPago()).isEqualByComparingTo("124600.00");
        assertThat(s.totalJuros()).isEqualByComparingTo("24600.00");

        SimulacaoFinanciamento semJuros = SimulacaoFinanciamento.calcular(new BigDecimal("12000"), BigDecimal.ZERO, 12, BigDecimal.ZERO);
        assertThat(semJuros.valorParcela()).isEqualByComparingTo("1000");

        assertThatThrownBy(() -> SimulacaoFinanciamento.calcular(new BigDecimal("1000"), new BigDecimal("1000"), 12, BigDecimal.ONE))
                .hasMessageContaining("entrada");
        assertThatThrownBy(() -> SimulacaoFinanciamento.calcular(new BigDecimal("1000"), BigDecimal.ZERO, 100, BigDecimal.ONE))
                .hasMessageContaining("parcelas");
    }

    @Test
    void dinheiroAceitaFormatosBrasileiros() {
        assertThat(Dinheiro.interpretar("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(Dinheiro.interpretar("89.900")).isEqualByComparingTo("89900");
        assertThat(Dinheiro.interpretar("1234.5")).isEqualByComparingTo("1234.5");
        assertThat(Dinheiro.interpretar("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(Dinheiro.interpretar("350")).isEqualByComparingTo("350");
        assertThat(Dinheiro.interpretar("  ")).isNull();
        assertThatThrownBy(() -> Dinheiro.interpretar("abc")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void categoriaDeDespesaPorSinonimo() {
        assertThat(CategoriaDespesa.interpretar("Troca de óleo")).isEqualTo(CategoriaDespesa.MECANICA);
        assertThat(CategoriaDespesa.interpretar("LAVAGEM")).isEqualTo(CategoriaDespesa.ESTETICA);
        assertThat(CategoriaDespesa.interpretar("IPVA e licenciamento")).isEqualTo(CategoriaDespesa.IPVA_LICENCIAMENTO);
        assertThat(CategoriaDespesa.interpretar("Funilaria e pintura")).isEqualTo(CategoriaDespesa.FUNILARIA_PINTURA);
        assertThat(CategoriaDespesa.interpretar("Coisa nova")).isEqualTo(CategoriaDespesa.OUTROS);
    }

    @Test
    void propostaValidaContatoEValor() {
        Anuncio anuncio = new Anuncio(polo(), dados("99900", 20000));
        assertThatThrownBy(() -> new Proposta(anuncio, new NovaProposta(TipoProposta.DUVIDA, "Ana", null, null, "Oi", null, null)))
                .hasMessageContaining("telefone ou e-mail");
        assertThatThrownBy(() -> new Proposta(anuncio, new NovaProposta(TipoProposta.PROPOSTA, "Ana", "19999998888", null, null, null, null)))
                .hasMessageContaining("proposta");
        assertThatThrownBy(() -> new Proposta(anuncio, new NovaProposta(TipoProposta.TEST_DRIVE, "Ana", "19999998888", null, null, null,
                LocalDate.now().minusDays(1)))).hasMessageContaining("passado");

        Proposta ok = new Proposta(anuncio, new NovaProposta(TipoProposta.PROPOSTA, "Ana Souza", "(19) 99999-8888",
                "ana@example.com", null, new BigDecimal("95000"), LocalDate.now()));
        assertThat(ok.getTelefoneFormatado()).isEqualTo("(19) 99999-8888");
        assertThat(ok.getLinkWhatsapp()).isEqualTo("https://wa.me/5519999998888");
        assertThat(ok.getDataPreferida()).isNull();
    }

    @Test
    void filtroDaLojaEOrdenacao() {
        Anuncio polo = new Anuncio(polo(), dados("99900", 20000));
        Anuncio onix = new Anuncio(fichaConcluida("XYZ9876", Map.of(CampoTecnico.MARCA, "Chevrolet", CampoTecnico.MODELO, "Onix",
                CampoTecnico.ANO_MODELO, "2020", CampoTecnico.MOTORIZACAO, "1.0", CampoTecnico.COMBUSTIVEL, "Flex",
                CampoTecnico.CAMBIO, "Manual de 6 marchas")), dados("64900", 61000));
        List<Anuncio> todos = List.of(polo, onix);

        assertThat(new FiltroLoja("polo", null, null, null, null, null, null, null).aplicar(todos)).containsExactly(polo);
        assertThat(new FiltroLoja(null, null, null, "Manual", null, null, null, null).aplicar(todos)).containsExactly(onix);
        assertThat(new FiltroLoja(null, null, null, null, new BigDecimal("70000"), null, null, null).aplicar(todos)).containsExactly(onix);
        assertThat(new FiltroLoja(null, null, null, null, null, 2022, null, null).aplicar(todos)).containsExactly(polo);
        assertThat(new FiltroLoja(null, null, null, null, null, null, null, "menor-preco").aplicar(todos)).containsExactly(onix, polo);
        assertThat(new FiltroLoja(null, "chevrolet", null, null, null, null, null, null).aplicar(todos)).containsExactly(onix);
    }
}
