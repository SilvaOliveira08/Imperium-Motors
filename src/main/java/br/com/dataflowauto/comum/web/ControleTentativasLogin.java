package br.com.dataflowauto.comum.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protege o login contra força bruta: depois de {@code maximoFalhas} senhas erradas do mesmo IP para o
 * mesmo usuário dentro da janela, esse par fica bloqueado pelo mesmo tempo, mesmo com a senha certa.
 * Fica em memória: reiniciar o sistema zera os contadores, o que é aceitável para um painel com um
 * usuário só.
 */
@Component
public class ControleTentativasLogin {

    /** Acima disso, os registros vencidos são limpos, para um ataque com muitos usuários não encher a memória. */
    private static final int LIMPAR_ACIMA_DE = 1_000;

    private final int maximoFalhas;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    @Autowired
    public ControleTentativasLogin(@Value("${dataflow.seguranca.login.maximo-falhas:5}") int maximoFalhas,
                                   @Value("${dataflow.seguranca.login.janela-minutos:15}") int janelaMinutos) {
        this(maximoFalhas, Duration.ofMinutes(janelaMinutos), Clock.systemUTC());
    }

    /** Permite controlar o relógio (usado nos testes). */
    public ControleTentativasLogin(int maximoFalhas, Duration janela, Clock relogio) {
        this.maximoFalhas = maximoFalhas;
        this.janela = janela;
        this.relogio = relogio;
    }

    public boolean bloqueado(String ip, String usuario) {
        Registro registro = registros.get(chave(ip, usuario));
        return registro != null && registro.bloqueadoAte != null && agora().isBefore(registro.bloqueadoAte);
    }

    /** Conta uma senha errada e devolve quantas falhas seguidas esse IP já tem para esse usuário. */
    public int registrarFalha(String ip, String usuario) {
        Instant agora = agora();
        if (registros.size() > LIMPAR_ACIMA_DE) {
            registros.values().removeIf(r -> r.vencido(agora, janela));
        }
        Registro registro = registros.compute(chave(ip, usuario), (k, atual) ->
                atual == null || atual.vencido(agora, janela) ? new Registro(agora) : atual);
        synchronized (registro) {
            registro.falhas++;
            if (registro.falhas >= maximoFalhas) {
                registro.bloqueadoAte = agora.plus(janela);
            }
            return registro.falhas;
        }
    }

    public void registrarSucesso(String ip, String usuario) {
        registros.remove(chave(ip, usuario));
    }

    public int getMaximoFalhas() {
        return maximoFalhas;
    }

    public Duration getJanela() {
        return janela;
    }

    private Instant agora() {
        return relogio.instant();
    }

    private static String chave(String ip, String usuario) {
        return ip + "|" + (usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT));
    }

    private static final class Registro {
        private final Instant primeiraFalha;
        private int falhas;
        private Instant bloqueadoAte;

        private Registro(Instant primeiraFalha) {
            this.primeiraFalha = primeiraFalha;
        }

        /** Passou a janela desde a primeira falha (ou desde o fim do bloqueio): a contagem recomeça. */
        private boolean vencido(Instant agora, Duration janela) {
            Instant fim = bloqueadoAte != null ? bloqueadoAte : primeiraFalha.plus(janela);
            return !agora.isBefore(fim);
        }
    }
}
