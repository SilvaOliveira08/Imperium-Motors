package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteDadosVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteIndisponivelException;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Integração com uma API de consulta veicular autorizada. Fica desligada até que
 * {@code dataflow.fichas.api-placa.url} seja configurada. Espera receber um JSON com
 * chaves iguais aos nomes do catálogo (ex.: {"MARCA": "Fiat", "MODELO": "Argo"}) ou
 * aos rótulos ("marca", "ano do modelo", "multas", "proprietario"); outros formatos exigem adaptar
 * {@link #converter}. Se a ficha tiver RENAVAM, ele vai junto como {@code ?renavam=}, já que muitas
 * consultas de multas e débitos pedem placa + RENAVAM.
 */
@Component
@Order(2)
public class FonteApiPlaca implements FonteDadosVeiculo {

    private static final Logger LOG = LoggerFactory.getLogger(FonteApiPlaca.class);
    // A consulta acontece enquanto o usuário espera a ficha: uma API travada não pode segurar a requisição
    // (nem a conexão com o banco) indefinidamente.
    static final Duration TEMPO_CONEXAO = Duration.ofSeconds(3);
    static final Duration TEMPO_LEITURA = Duration.ofSeconds(5);

    private final String url;
    private final String token;
    private final RestClient cliente;

    @Autowired
    public FonteApiPlaca(@Value("${dataflow.fichas.api-placa.url:}") String url,
                         @Value("${dataflow.fichas.api-placa.token:}") String token) {
        this(url, token, TEMPO_CONEXAO, TEMPO_LEITURA);
    }

    /** Permite escolher os tempos limite (usado nos testes para não esperar 5 segundos). */
    public FonteApiPlaca(String url, String token, Duration tempoConexao, Duration tempoLeitura) {
        this.url = url;
        this.token = token;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tempoConexao);
        fabrica.setReadTimeout(tempoLeitura);
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String nome() {
        return "API de consulta por placa";
    }

    @Override
    public OrigemDado origem() {
        return OrigemDado.FONTE_EXTERNA;
    }

    @Override
    public boolean podeAtender(ConsultaVeiculo consulta) {
        return url != null && !url.isBlank();
    }

    @Override
    public Map<CampoTecnico, String> buscar(ConsultaVeiculo consulta) {
        try {
            Map<String, Object> resposta = cliente.get()
                    .uri(consulta.possuiRenavam() ? url + "/{placa}?renavam={renavam}" : url + "/{placa}",
                            consulta.possuiRenavam()
                                    ? new Object[]{consulta.placa().valor(), consulta.renavam()}
                                    : new Object[]{consulta.placa().valor()})
                    .headers(h -> {
                        if (!token.isBlank()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return converter(resposta);
        } catch (RestClientException e) {
            // só o tipo do erro: a mensagem pode trazer a URL com placa e RENAVAM, ou o corpo da resposta
            LOG.warn("Falha ao consultar a API de placa ({})", e.getClass().getSimpleName());
            // sem a causa: a mensagem dela pode trazer a URL com placa e RENAVAM
            throw new FonteIndisponivelException(nome());
        }
    }

    Map<CampoTecnico, String> converter(Map<String, Object> resposta) {
        Map<CampoTecnico, String> dados = new EnumMap<>(CampoTecnico.class);
        if (resposta == null) {
            return dados;
        }
        resposta.forEach((chave, valor) -> {
            if (valor == null) {
                return;
            }
            Optional<CampoTecnico> campo = CampoTecnico.porRotulo(chave);
            campo.ifPresent(c -> dados.put(c, valor.toString()));
        });
        return dados;
    }
}
