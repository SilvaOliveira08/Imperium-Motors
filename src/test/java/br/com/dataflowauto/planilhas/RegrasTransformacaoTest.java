package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.dominio.regras.FiltrarVazios;
import br.com.dataflowauto.planilhas.dominio.regras.PadronizarTexto;
import br.com.dataflowauto.planilhas.dominio.regras.RegraTransformacao;
import br.com.dataflowauto.planilhas.dominio.regras.RemoverDuplicatas;
import br.com.dataflowauto.planilhas.dominio.regras.RenomearColuna;
import br.com.dataflowauto.planilhas.dominio.regras.ReordenarColunas;
import br.com.dataflowauto.planilhas.dominio.regras.SelecionarColunas;
import br.com.dataflowauto.planilhas.dominio.regras.TipoPadronizacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.coluna;
import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegrasTransformacaoTest {

    private final Planilha clientes = planilha("Clientes", List.of("Nome", "CPF", "Cidade"),
            "ana silva", "12345678900", "Campinas",
            "Bruno", "98765432100", "",
            "ana silva", "123.456.789-00", "Campinas");

    @Test
    void selecionarMantemSoAsColunasNaOrdemPedida() {
        Planilha r = new SelecionarColunas(List.of("CPF", "Nome")).aplicar(clientes);
        assertThat(r.getNomesColunas()).containsExactly("CPF", "Nome");
        assertThat(r.getLinhas().get(0).getValores()).containsOnlyKeys("CPF", "Nome");
    }

    @Test
    void selecionarColunaInexistenteFalha() {
        assertThatThrownBy(() -> new SelecionarColunas(List.of("Telefone")).aplicar(clientes))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void reordenarMantemColunasNaoCitadasNoFinal() {
        Planilha r = new ReordenarColunas(List.of("Cidade")).aplicar(clientes);
        assertThat(r.getNomesColunas()).containsExactly("Cidade", "Nome", "CPF");
    }

    @Test
    void renomearTrocaCabecalhoEValores() {
        Planilha r = new RenomearColuna("Cidade", "Município").aplicar(clientes);
        assertThat(r.getNomesColunas()).containsExactly("Nome", "CPF", "Município");
        assertThat(r.getLinhas().get(0).getValor("Município")).isEqualTo("Campinas");
    }

    @Test
    void renomearParaNomeJaExistenteFalha() {
        assertThatThrownBy(() -> new RenomearColuna("Cidade", "Nome").aplicar(clientes))
                .hasMessageContaining("Já existe");
    }

    @Test
    void removerDuplicatasPorTodasAsColunas() {
        Planilha p = planilha("P", List.of("A", "B"), "1", "x", "1", "X ", "2", "y", "", "");
        Planilha r = new RemoverDuplicatas().aplicar(p);
        assertThat(coluna(r, "A")).containsExactly("1", "2");
    }

    @Test
    void removerDuplicatasPorColunaChaveDepoisDePadronizar() {
        // CPF escrito de duas formas só vira duplicata depois de padronizado
        assertThat(new RemoverDuplicatas(List.of("CPF")).aplicar(clientes).totalLinhas()).isEqualTo(3);

        Planilha padronizada = new PadronizarTexto("CPF", TipoPadronizacao.CPF).aplicar(clientes);
        Planilha r = new RemoverDuplicatas(List.of("CPF")).aplicar(padronizada);
        assertThat(coluna(r, "CPF")).containsExactly("123.456.789-00", "987.654.321-00");
    }

    @Test
    void removerDuplicatasPorChaveMantemLinhasComChaveVazia() {
        Planilha p = planilha("P", List.of("Nome", "CPF"), "Ana", "1", "Ana", "1", "Elisa", "", "Caio", "");
        Planilha r = new RemoverDuplicatas(List.of("CPF")).aplicar(p);
        assertThat(coluna(r, "Nome")).containsExactly("Ana", "Elisa", "Caio");
    }

    @Test
    void filtrarVaziosRemoveLinhasSemValorNaColuna() {
        Planilha r = new FiltrarVazios("Cidade").aplicar(clientes);
        assertThat(coluna(r, "Nome")).containsExactly("ana silva", "ana silva");
    }

    @Test
    void regrasAplicadasEmSequenciaPorPolimorfismo() {
        List<RegraTransformacao> regras = List.of(
                new PadronizarTexto("Nome", TipoPadronizacao.NOME_PROPRIO),
                new PadronizarTexto("CPF", TipoPadronizacao.CPF),
                new RemoverDuplicatas(),
                new SelecionarColunas(List.of("CPF", "Nome")),
                new RenomearColuna("Nome", "Cliente"));
        Planilha r = clientes;
        for (RegraTransformacao regra : regras) {
            r = regra.aplicar(r);
        }
        assertThat(r.getNomesColunas()).containsExactly("CPF", "Cliente");
        assertThat(coluna(r, "Cliente")).containsExactly("Ana Silva", "Bruno");
        // a planilha original não muda
        assertThat(clientes.totalLinhas()).isEqualTo(3);
        assertThat(clientes.getLinhas().get(0).getValor("Nome")).isEqualTo("ana silva");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CPF        | 12345678900        | 123.456.789-00",
            "CPF        | 123                | 123",
            "CNPJ       | 11222333000181     | 11.222.333/0001-81",
            "PLACA      | abc-1234           | ABC1234",
            "PLACA      | bra 2e19           | BRA2E19",
            "PLACA      | 12345              | 12345",
            "TELEFONE   | 19987654321        | (19) 98765-4321",
            "TELEFONE   | +55 19 3333-4444   | (19) 3333-4444",
            "DATA       | 2024-03-05         | 05/03/2024",
            "DATA       | 5/3/2024           | 05/03/2024",
            "DATA       | ontem              | ontem",
            "MAIUSCULAS | São Paulo          | SÃO PAULO",
            "SEM_ACENTOS| São Paulo          | Sao Paulo",
            "NOME_PROPRIO | MARIA DA SILVA   | Maria da Silva",
            "REMOVER_ESPACOS | '  a   b '   | a b",
            "SOMENTE_NUMEROS | R$ 1.234     | 1234"
    })
    void padronizacoes(TipoPadronizacao tipo, String entrada, String esperado) {
        assertThat(tipo.aplicar(entrada)).isEqualTo(esperado);
    }

    @Test
    void padronizacaoMantemVazio() {
        assertThat(TipoPadronizacao.CPF.aplicar("")).isEmpty();
        assertThat(TipoPadronizacao.CPF.aplicar(null)).isEmpty();
    }
}
