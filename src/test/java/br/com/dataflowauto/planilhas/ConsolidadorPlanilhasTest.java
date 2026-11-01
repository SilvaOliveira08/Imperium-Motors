package br.com.dataflowauto.planilhas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.ConsolidadorPlanilhas;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.dominio.TipoJuncao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.dataflowauto.planilhas.PlanilhasTeste.coluna;
import static br.com.dataflowauto.planilhas.PlanilhasTeste.planilha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsolidadorPlanilhasTest {

    private final ConsolidadorPlanilhas consolidador = new ConsolidadorPlanilhas();

    private final Planilha clientes = planilha("Clientes", List.of("CPF", "Nome"),
            "123.456.789-00", "Ana",
            "987.654.321-00", "Bruno",
            "111.111.111-11", "Carla");

    private final Planilha veiculos = planilha("Veiculos", List.of("Documento", "Placa", "Nome"),
            "12345678900", "ABC1D23", "Ana S.",
            "98765432100", "BRA2E19", "Bruno M.",
            "98765432100", "XYZ9876", "Bruno M.",
            "55555555555", "QWE1234", "Diego");

    @Test
    void juncaoInternaIgnoraPontuacaoNaChave() {
        Planilha r = consolidador.combinar(clientes, veiculos, "CPF", "Documento", TipoJuncao.INTERNA);
        assertThat(r.getNomesColunas()).containsExactly("CPF", "Nome", "Placa", "Nome (Veiculos)");
        assertThat(coluna(r, "Nome")).containsExactly("Ana", "Bruno", "Bruno");
        assertThat(coluna(r, "Placa")).containsExactly("ABC1D23", "BRA2E19", "XYZ9876");
    }

    @Test
    void combinacaoQuePassaDoTetoDeLinhasEhRecusada() {
        // "Bruno" aparece duas vezes na segunda: a interna dá 3 linhas, uma a mais que o teto de 2
        ConsolidadorPlanilhas comTeto = new ConsolidadorPlanilhas(2);
        assertThatThrownBy(() -> comTeto.combinar(clientes, veiculos, "CPF", "Documento", TipoJuncao.INTERNA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("passaria de 2 linhas");
        assertThat(new ConsolidadorPlanilhas(3).combinar(clientes, veiculos, "CPF", "Documento", TipoJuncao.INTERNA)
                .totalLinhas()).isEqualTo(3);
    }

    @Test
    void juncaoEsquerdaMantemQuemNaoTemCorrespondencia() {
        Planilha r = consolidador.combinar(clientes, veiculos, "CPF", "Documento", TipoJuncao.ESQUERDA);
        assertThat(coluna(r, "Nome")).containsExactly("Ana", "Bruno", "Bruno", "Carla");
        assertThat(r.getLinhas().get(3).getValor("Placa")).isEmpty();
    }

    @Test
    void juncaoCompletaIncluiLinhasDaSegundaSemCorrespondencia() {
        Planilha r = consolidador.combinar(clientes, veiculos, "CPF", "Documento", TipoJuncao.COMPLETA);
        assertThat(r.totalLinhas()).isEqualTo(5);
        assertThat(r.getLinhas().get(4).getValor("CPF")).isEqualTo("55555555555");
        assertThat(r.getLinhas().get(4).getValor("Placa")).isEqualTo("QWE1234");
    }

    @Test
    void chaveInexistenteGeraErro() {
        assertThatThrownBy(() -> consolidador.combinar(clientes, veiculos, "Email", "Documento", TipoJuncao.INTERNA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void chaveVaziaNaoCasaComNada() {
        Planilha a = planilha("A", List.of("K", "V"), "", "a1", "1", "a2");
        Planilha b = planilha("B", List.of("K", "W"), "", "b1", "1", "b2");
        Planilha r = consolidador.combinar(a, b, "K", "K", TipoJuncao.INTERNA);
        assertThat(coluna(r, "W")).containsExactly("b2");
    }
}
