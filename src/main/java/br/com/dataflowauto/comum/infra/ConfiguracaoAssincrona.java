package br.com.dataflowauto.comum.infra;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async}: avisos que dependem de serviços externos (ex.: e-mail de novo contato)
 * rodam fora da thread da requisição, para o visitante não esperar por eles.
 */
@Configuration
@EnableAsync
public class ConfiguracaoAssincrona {
}
