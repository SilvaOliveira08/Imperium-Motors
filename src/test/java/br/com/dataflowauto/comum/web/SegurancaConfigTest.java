package br.com.dataflowauto.comum.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegurancaConfigTest {

    @Test
    void semSenhaOSistemaNaoInicia() {
        assertThatThrownBy(() -> SegurancaConfig.validarSenha(null, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DATAFLOW_ADMIN_SENHA");
        assertThatThrownBy(() -> SegurancaConfig.validarSenha("   ", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void senhaPadraoSoNoPerfilLocal() {
        assertThatThrownBy(() -> SegurancaConfig.validarSenha("dataflow123", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("perfil local");
        assertThatCode(() -> SegurancaConfig.validarSenha("dataflow123", true)).doesNotThrowAnyException();
    }

    @Test
    void senhaPropriaPrecisaDeTamanhoMinimo() {
        assertThatThrownBy(() -> SegurancaConfig.validarSenha("curta123", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("10 caracteres");
        assertThatCode(() -> SegurancaConfig.validarSenha("uma-senha-bem-longa", false)).doesNotThrowAnyException();
    }
}
