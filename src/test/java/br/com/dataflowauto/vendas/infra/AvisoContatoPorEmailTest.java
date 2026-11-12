package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.vendas.aplicacao.ContatoRecebido;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AvisoContatoPorEmailTest {

    private static final ContatoRecebido CONTATO = new ContatoRecebido(7L, "Proposta", "Fiat Strada 2022",
            "Carlos\r\nBcc: intruso@x.com", "19991234567", "carlos@email.com", "Aceita troca?",
            new BigDecimal("98500"), LocalDate.of(2026, 11, 20));

    private static AvisoContatoPorEmail novo(RestClient cliente, String chave, String destinatario) {
        return new AvisoContatoPorEmail(cliente, chave, "Imperium Motors <onboarding@resend.dev>", destinatario,
                "https://imperium.onrender.com/");
    }

    @Test
    void enviaOAvisoPeloResendComOsDadosDoContato() {
        RestClient.Builder construtor = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_teste"))
                .andExpect(jsonPath("$.to[0]").value("loja@imperium.com"))
                .andExpect(jsonPath("$.reply_to").value("carlos@email.com"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        AvisoContatoPorEmail aviso = novo(construtor.build(), "re_teste", "loja@imperium.com");

        assertThat(aviso.enviar(CONTATO)).isTrue();
        servidor.verify();
    }

    @Test
    void falhaDoResendNaoLancaExcecao() {
        RestClient.Builder construtor = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();
        servidor.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

        AvisoContatoPorEmail aviso = novo(construtor.build(), "re_teste", "loja@imperium.com");

        assertThat(aviso.enviar(CONTATO)).isFalse();
    }

    @Test
    void ficaDesligadoSemChaveOuSemDestinatario() {
        RestClient cliente = RestClient.builder().baseUrl("http://localhost:1").build();

        assertThat(novo(cliente, "", "loja@imperium.com").ativo()).isFalse();
        assertThat(novo(cliente, "re_teste", "").ativo()).isFalse();
        assertThat(novo(cliente, "", "").enviar(CONTATO)).isFalse();
    }

    @Test
    void textoTrazOsDadosEOAssuntoNaoAceitaQuebraDeLinha() {
        AvisoContatoPorEmail aviso = novo(
                RestClient.builder().build(), "re_teste", "loja@imperium.com");

        String texto = aviso.texto(CONTATO);
        assertThat(texto).contains("Carro: Fiat Strada 2022", "Telefone: 19991234567", "20/11/2026",
                "Aceita troca?", "https://imperium.onrender.com/propostas");
        assertThat(texto).contains("98.500,00");
        assertThat(AvisoContatoPorEmail.assunto(CONTATO)).doesNotContain("\r", "\n");
    }
}
