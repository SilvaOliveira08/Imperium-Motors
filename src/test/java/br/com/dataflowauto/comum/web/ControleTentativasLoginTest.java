package br.com.dataflowauto.comum.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ControleTentativasLoginTest {

    /** Relógio que o teste avança à mão. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-11-01T10:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final ControleTentativasLogin controle = new ControleTentativasLogin(5, Duration.ofMinutes(15), relogio);

    private void errar(String ip, String usuario, int vezes) {
        for (int i = 0; i < vezes; i++) {
            controle.registrarFalha(ip, usuario);
        }
    }

    @Test
    void cincoFalhasSeguidasBloqueiamSoAqueleIpEUsuario() {
        errar("10.0.0.1", "admin", 4);
        assertThat(controle.bloqueado("10.0.0.1", "admin")).isFalse();

        assertThat(controle.registrarFalha("10.0.0.1", "ADMIN ")).isEqualTo(5);
        assertThat(controle.bloqueado("10.0.0.1", "admin")).isTrue();

        assertThat(controle.bloqueado("10.0.0.2", "admin")).isFalse();
        assertThat(controle.bloqueado("10.0.0.1", "outro")).isFalse();
    }

    @Test
    void bloqueioAcabaDepoisDaJanela() {
        errar("10.0.0.1", "admin", 5);
        relogio.avancar(Duration.ofMinutes(14));
        assertThat(controle.bloqueado("10.0.0.1", "admin")).isTrue();

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(controle.bloqueado("10.0.0.1", "admin")).isFalse();
        // a contagem recomeça do zero
        assertThat(controle.registrarFalha("10.0.0.1", "admin")).isEqualTo(1);
    }

    @Test
    void falhasEspalhadasAlemDaJanelaNaoSeAcumulam() {
        errar("10.0.0.1", "admin", 4);
        relogio.avancar(Duration.ofMinutes(16));

        assertThat(controle.registrarFalha("10.0.0.1", "admin")).isEqualTo(1);
        assertThat(controle.bloqueado("10.0.0.1", "admin")).isFalse();
    }

    @Test
    void loginCertoZeraAsFalhas() {
        errar("10.0.0.1", "admin", 4);
        controle.registrarSucesso("10.0.0.1", "admin");

        assertThat(controle.registrarFalha("10.0.0.1", "admin")).isEqualTo(1);
    }

    @Test
    void usuarioDoFormularioNaoQuebraLinhasNoLog() {
        assertThat(SegurancaConfig.paraLog("admin\r\n2026-11-01 INFO login aceito")).doesNotContain("\n", "\r");
        assertThat(SegurancaConfig.paraLog("x".repeat(200))).hasSize(61);
        assertThat(SegurancaConfig.paraLog(null)).isEmpty();
    }
}
