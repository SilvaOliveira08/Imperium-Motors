package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.planilhas.dominio.AnalisadorQualidade;
import br.com.dataflowauto.planilhas.dominio.RelatorioQualidade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;

class AnalisadorQualidadeTest {

    @Test
    void contaDuplicatasEVazios() {
        RelatorioQualidade r = new AnalisadorQualidade().analisar(planilha("P", List.of("A", "B"),
                "1", "x",
                "1", "x",
                "2", "",
                "", ""));
        assertThat(r.totalLinhas()).isEqualTo(4);
        assertThat(r.linhasDuplicadas()).isEqualTo(1);
        assertThat(r.linhasTotalmenteVazias()).isEqualTo(1);
        assertThat(r.vaziosPorColuna()).containsEntry("A", 1).containsEntry("B", 2);
        assertThat(r.percentualVazio("B")).isEqualTo(50);
        assertThat(r.semProblemas()).isFalse();
    }
}
