package br.com.dataflowauto.fichas;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.fichas.dominio.StatusVerificacao;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FichaTecnicaTest {

    private FichaTecnica novaFicha() {
        return new FichaTecnica(new Veiculo(new Placa("ABC1D23")));
    }

    @Test
    void fonteQueNaoRespondeuDeixaAConsultaIncompleta() {
        FichaTecnica ficha = novaFicha();
        ficha.registrarFontesConsultadas(List.of("Base de demonstração (3 encontrados, 3 usados)"));
        assertThat(ficha.consultaIncompleta()).isFalse();

        ficha.registrarFontesConsultadas(List.of(FichaTecnica.fonteIndisponivel("API de consulta por placa")));
        assertThat(ficha.consultaIncompleta()).isTrue();
    }

    @Test
    void nasceComTodosOsCamposAusentes() {
        FichaTecnica ficha = novaFicha();
        assertThat(ficha.getCampos()).hasSize(CampoTecnico.values().length);
        assertThat(ficha.contar(StatusVerificacao.AUSENTE)).isEqualTo(CampoTecnico.values().length);
        assertThat(ficha.getStatus()).isEqualTo(StatusFicha.EM_REVISAO);
        assertThat(ficha.estaCompleta()).isFalse();
    }

    @Test
    void dadoDeDocumentoFicaAConfirmarEDadoDoUsuarioJaEntraConfirmado() {
        FichaTecnica ficha = novaFicha();
        ficha.registrarDado(CampoTecnico.MARCA, "Fiat", OrigemDado.DOCUMENTO, "CRLV.pdf");
        ficha.registrarDado(CampoTecnico.MODELO, "Argo", OrigemDado.USUARIO, null);

        assertThat(ficha.campo(CampoTecnico.MARCA).getStatus()).isEqualTo(StatusVerificacao.A_CONFIRMAR);
        assertThat(ficha.campo(CampoTecnico.MARCA).getFonteDetalhe()).isEqualTo("CRLV.pdf");
        assertThat(ficha.campo(CampoTecnico.MODELO).getStatus()).isEqualTo(StatusVerificacao.CONFIRMADO);
        assertThat(ficha.valorConfirmado(CampoTecnico.MARCA)).isEmpty();
        assertThat(ficha.valorConfirmado(CampoTecnico.MODELO)).contains("Argo");
    }

    @Test
    void primeiraFonteTemPrioridade() {
        FichaTecnica ficha = novaFicha();
        assertThat(ficha.registrarDado(CampoTecnico.MARCA, "Fiat", OrigemDado.DOCUMENTO, null)).isTrue();
        assertThat(ficha.registrarDado(CampoTecnico.MARCA, "Ford", OrigemDado.FONTE_EXTERNA, null)).isFalse();
        assertThat(ficha.campo(CampoTecnico.MARCA).getValor()).isEqualTo("Fiat");
    }

    @Test
    void editarComValorNovoPassaAOrigemDoUsuario() {
        FichaTecnica ficha = novaFicha();
        ficha.registrarDado(CampoTecnico.POTENCIA, "100 cv", OrigemDado.FONTE_EXTERNA, null);

        ficha.editar(CampoTecnico.POTENCIA, "100 cv");
        assertThat(ficha.campo(CampoTecnico.POTENCIA).getStatus()).isEqualTo(StatusVerificacao.A_CONFIRMAR);

        ficha.editar(CampoTecnico.POTENCIA, "105 cv");
        assertThat(ficha.campo(CampoTecnico.POTENCIA).getOrigem()).isEqualTo(OrigemDado.USUARIO);
        assertThat(ficha.campo(CampoTecnico.POTENCIA).getStatus()).isEqualTo(StatusVerificacao.CONFIRMADO);

        ficha.editar(CampoTecnico.POTENCIA, "  ");
        assertThat(ficha.campo(CampoTecnico.POTENCIA).getStatus()).isEqualTo(StatusVerificacao.AUSENTE);
    }

    @Test
    void naoConfirmaCampoSemValor() {
        FichaTecnica ficha = novaFicha();
        assertThatThrownBy(() -> ficha.confirmar(CampoTecnico.TORQUE)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void soConcluiComObrigatoriosConfirmadosESemPendencias() {
        FichaTecnica ficha = novaFicha();
        Arrays.stream(CampoTecnico.values()).filter(CampoTecnico::isObrigatorio)
                .forEach(c -> ficha.registrarDado(c, "valor", OrigemDado.DOCUMENTO, null));
        ficha.registrarDado(CampoTecnico.COR, "Prata", OrigemDado.DOCUMENTO, null);

        assertThatThrownBy(ficha::concluir).isInstanceOf(RegraNegocioException.class).hasMessageContaining("Marca");

        Arrays.stream(CampoTecnico.values()).filter(CampoTecnico::isObrigatorio).forEach(ficha::confirmar);
        assertThat(ficha.camposPendentes()).extracting(c -> c.getCampo()).containsExactly(CampoTecnico.COR);

        ficha.confirmar(CampoTecnico.COR);
        ficha.concluir();
        assertThat(ficha.getStatus()).isEqualTo(StatusFicha.CONCLUIDA);
        assertThat(ficha.getVeiculo().getMarca()).isEqualTo("valor");
    }

    @Test
    void edicaoQueGeraPendenciaReabreFichaConcluida() {
        FichaTecnica ficha = novaFicha();
        Arrays.stream(CampoTecnico.values()).filter(CampoTecnico::isObrigatorio)
                .forEach(c -> ficha.registrarDado(c, "x", OrigemDado.USUARIO, null));
        ficha.concluir();
        ficha.editar(CampoTecnico.MARCA, "");
        assertThat(ficha.getStatus()).isEqualTo(StatusFicha.EM_REVISAO);
    }

    @Test
    void placaAceitaPadraoAntigoEMercosul() {
        assertThat(new Placa("abc-1234").valor()).isEqualTo("ABC1234");
        assertThat(new Placa("abc-1234").formatada()).isEqualTo("ABC-1234");
        assertThat(new Placa("bra2e19").ehMercosul()).isTrue();
        assertThatThrownBy(() -> new Placa("12ABC34")).isInstanceOf(RegraNegocioException.class);
    }
}
