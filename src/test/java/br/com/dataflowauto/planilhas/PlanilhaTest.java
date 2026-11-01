package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanilhaTest {

    @Test
    void naoAceitaColunasRepetidas() {
        assertThatThrownBy(() -> Planilha.deNomes("X", List.of("A", "A"), List.of()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void buscarColunaInexistenteGeraMensagemClara() {
        Planilha p = planilha("Clientes", List.of("Nome"), "Ana");
        assertThatThrownBy(() -> p.getColuna("CPF"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    void paginaDevolveOIntervaloCerto() {
        Planilha p = planilha("P", List.of("N"), "1", "2", "3", "4", "5");
        assertThat(p.pagina(2, 2)).extracting(l -> l.getValor("N")).containsExactly("3", "4");
        assertThat(p.pagina(3, 2)).extracting(l -> l.getValor("N")).containsExactly("5");
        assertThat(p.pagina(9, 2)).isEmpty();
        assertThat(p.totalPaginas(2)).isEqualTo(3);
    }

    @Test
    void listaDeLinhasNaoPodeSerAlteradaPorFora() {
        Planilha p = planilha("P", List.of("N"), "1");
        assertThatThrownBy(() -> p.getLinhas().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
