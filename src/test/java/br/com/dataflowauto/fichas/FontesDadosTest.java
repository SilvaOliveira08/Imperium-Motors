package br.com.dataflowauto.fichas;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteIndisponivelException;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.infra.FonteApiPlaca;
import br.com.dataflowauto.fichas.infra.FonteDemonstracao;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FontesDadosTest {

    @Test
    void fonteDemonstracaoSoAtendePlacasConhecidasEQuandoHabilitada() {
        ConsultaVeiculo conhecida = ConsultaVeiculo.somentePlaca(new Placa("ABC1D23"));
        ConsultaVeiculo desconhecida = ConsultaVeiculo.somentePlaca(new Placa("AAA0000"));

        FonteDemonstracao ligada = new FonteDemonstracao(true);
        assertThat(ligada.podeAtender(conhecida)).isTrue();
        assertThat(ligada.podeAtender(desconhecida)).isFalse();
        assertThat(ligada.buscar(conhecida)).containsEntry(CampoTecnico.MARCA, "Volkswagen");

        assertThat(new FonteDemonstracao(false).podeAtender(conhecida)).isFalse();
    }

    @Test
    void apiPlacaFicaDesligadaSemUrl() {
        assertThat(new FonteApiPlaca("", "").podeAtender(ConsultaVeiculo.somentePlaca(new Placa("ABC1D23")))).isFalse();
        assertThat(new FonteApiPlaca("http://localhost:1", "").podeAtender(
                ConsultaVeiculo.somentePlaca(new Placa("ABC1D23")))).isTrue();
    }

    @Test
    void apiPlacaTravadaNaoSeguraARequisicao() throws Exception {
        // servidor que aceita a conexão e nunca responde, como uma API fora do ar
        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread aceitador = new Thread(() -> {
                try {
                    servidor.accept();
                } catch (IOException e) {
                    // servidor fechado no fim do teste
                }
            });
            aceitador.setDaemon(true);
            aceitador.start();

            FonteApiPlaca api = new FonteApiPlaca("http://localhost:" + servidor.getLocalPort(), "",
                    Duration.ofMillis(500), Duration.ofMillis(500));
            long inicio = System.nanoTime();

            // falhar não pode parecer "consultou e não achou nada"
            assertThatThrownBy(() -> api.buscar(ConsultaVeiculo.somentePlaca(new Placa("ABC1D23"))))
                    .isInstanceOf(FonteIndisponivelException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(5));
        }
    }
}
