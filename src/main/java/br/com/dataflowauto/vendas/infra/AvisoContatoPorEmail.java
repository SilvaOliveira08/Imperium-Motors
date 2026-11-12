package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.vendas.aplicacao.ContatoRecebido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Avisa a equipe por e-mail, pela API do Resend, quando chega um contato pelo site.
 * Fica desligado enquanto {@code RESEND_API_KEY} ou {@code DATAFLOW_AVISO_EMAIL} estiverem vazios.
 * Roda depois do commit e em outra thread: se o Resend demorar ou cair, o visitante não espera
 * e o contato continua gravado (a equipe vê no painel do mesmo jeito).
 */
@Component
public class AvisoContatoPorEmail {

    static final String URL_RESEND = "https://api.resend.com";
    private static final Logger LOG = LoggerFactory.getLogger(AvisoContatoPorEmail.class);
    private static final Pattern EMAIL_SIMPLES = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestClient cliente;
    private final String chave;
    private final String remetente;
    private final String destinatario;
    private final String site;

    @Autowired
    public AvisoContatoPorEmail(RestClient.Builder construtor,
                                @Value("${dataflow.avisos.resend.chave:}") String chave,
                                @Value("${dataflow.avisos.remetente:Imperium Motors <onboarding@resend.dev>}") String remetente,
                                @Value("${dataflow.avisos.destinatario:}") String destinatario,
                                @Value("${dataflow.loja.site:http://localhost:8080}") String site) {
        this(construtor.requestFactory(fabricaComTempoLimite()).baseUrl(URL_RESEND).build(),
                chave, remetente, destinatario, site);
    }

    /** Recebe o cliente HTTP pronto (usado nos testes, com um servidor falso). */
    AvisoContatoPorEmail(RestClient cliente, String chave, String remetente, String destinatario, String site) {
        this.cliente = cliente;
        this.chave = chave == null ? "" : chave.trim();
        this.remetente = remetente;
        this.destinatario = destinatario == null ? "" : destinatario.trim();
        this.site = site == null ? "" : site.replaceAll("/+$", "");
    }

    private static SimpleClientHttpRequestFactory fabricaComTempoLimite() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return fabrica;
    }

    public boolean ativo() {
        return !chave.isEmpty() && !destinatario.isEmpty();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoReceberContato(ContatoRecebido contato) {
        enviar(contato);
    }

    /** Devolve se o e-mail foi aceito pelo Resend. Nunca lança: falha no aviso não pode derrubar o contato. */
    boolean enviar(ContatoRecebido contato) {
        if (!ativo()) {
            return false;
        }
        try {
            cliente.post()
                    .uri("/emails")
                    .headers(h -> h.setBearerAuth(chave))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo(contato))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // só o tipo do erro e o número do contato: nada de dados do cliente no log
            LOG.warn("Não foi possível enviar o aviso do contato {} por e-mail ({})",
                    contato.propostaId(), e.getClass().getSimpleName());
            return false;
        }
    }

    Map<String, Object> corpo(ContatoRecebido contato) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("from", remetente);
        corpo.put("to", List.of(destinatario));
        corpo.put("subject", assunto(contato));
        corpo.put("text", texto(contato));
        // "responder" no e-mail já vai para o cliente, se ele deixou um endereço válido
        if (contato.email() != null && EMAIL_SIMPLES.matcher(contato.email().trim()).matches()) {
            corpo.put("reply_to", contato.email().trim());
        }
        return corpo;
    }

    static String assunto(ContatoRecebido contato) {
        // quebras de linha no assunto viram espaço (o nome vem de um formulário público)
        return linha(contato.tipo() + " de " + contato.nome() + " - " + contato.carro());
    }

    String texto(ContatoRecebido contato) {
        StringBuilder texto = new StringBuilder("Novo contato pelo site\n\n");
        campo(texto, "Tipo", contato.tipo());
        campo(texto, "Carro", contato.carro());
        campo(texto, "Nome", contato.nome());
        campo(texto, "Telefone", contato.telefone());
        campo(texto, "E-mail", contato.email());
        campo(texto, "Valor oferecido", contato.valorOferecido() == null ? null : reais(contato.valorOferecido()));
        campo(texto, "Data preferida", contato.dataPreferida() == null ? null : DATA.format(contato.dataPreferida()));
        if (contato.mensagem() != null && !contato.mensagem().isBlank()) {
            texto.append("\nMensagem:\n").append(contato.mensagem().trim()).append('\n');
        }
        texto.append("\nVeja e responda no painel: ").append(site).append("/propostas\n");
        return texto.toString();
    }

    private static void campo(StringBuilder texto, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            texto.append(rotulo).append(": ").append(linha(valor)).append('\n');
        }
    }

    private static String linha(String valor) {
        return valor == null ? "" : valor.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String reais(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
    }
}
