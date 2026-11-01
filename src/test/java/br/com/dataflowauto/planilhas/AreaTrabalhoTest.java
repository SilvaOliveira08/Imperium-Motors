package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.web.AreaTrabalho;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.coluna;
import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaTrabalhoTest {

    /** Etapa de teste: tira a primeira linha. */
    private static final UnaryOperator<Planilha> TIRAR_PRIMEIRA = p -> p.comLinhas(p.getLinhas().subList(1, p.totalLinhas()));

    private static Planilha numerada(int linhas) {
        List<String> valores = new ArrayList<>();
        for (int i = 1; i <= linhas; i++) {
            valores.add(String.valueOf(i));
        }
        return planilha("Numeros", List.of("N"), valores.toArray(String[]::new));
    }

    private static void aplicar(AreaTrabalho area, int vezes) {
        for (int i = 0; i < vezes; i++) {
            area.registrar(TIRAR_PRIMEIRA.apply(area.getAtual()), new AreaTrabalho.Etapa("Tirar a primeira", TIRAR_PRIMEIRA));
        }
    }

    @Test
    void desfazerReaplicaAsEtapasQueSobraram() {
        AreaTrabalho area = new AreaTrabalho();
        area.iniciar(numerada(10));
        aplicar(area, 3);
        assertThat(coluna(area.getAtual(), "N")).startsWith("4");

        area.desfazer();
        assertThat(coluna(area.getAtual(), "N")).startsWith("3").hasSize(8);
        area.desfazer();
        area.desfazer();
        assertThat(area.getAtual().totalLinhas()).isEqualTo(10);
        assertThat(area.podeDesfazer()).isFalse();
        assertThat(area.getEtapas()).containsExactly("Planilha carregada: Numeros");
    }

    @Test
    void depoisDe20EtapasAsMaisAntigasNaoPodemMaisSerDesfeitas() {
        AreaTrabalho area = new AreaTrabalho();
        area.iniciar(numerada(30));
        aplicar(area, 25);
        assertThat(area.getAtual().totalLinhas()).isEqualTo(5);

        for (int i = 0; i < 20; i++) {
            area.desfazer();
        }
        // as 5 primeiras etapas viraram parte da planilha de partida
        assertThat(area.getAtual().totalLinhas()).isEqualTo(25);
        assertThat(coluna(area.getAtual(), "N")).startsWith("6");
        assertThatThrownBy(area::desfazer).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void formularioDeOutraPlanilhaEhRecusado() {
        AreaTrabalho area = new AreaTrabalho();
        area.iniciar(numerada(3));
        String primeira = area.getId();
        area.conferir(primeira);

        area.iniciar(numerada(5));
        assertThat(area.getId()).isNotEqualTo(primeira);
        assertThatThrownBy(() -> area.conferir(primeira))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("outra aba");
        assertThatThrownBy(() -> area.conferir(null)).isInstanceOf(RegraNegocioException.class);
        area.conferir(area.getId());
    }
}
