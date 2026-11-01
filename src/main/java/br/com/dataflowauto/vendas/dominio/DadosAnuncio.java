package br.com.dataflowauto.vendas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados comerciais do anúncio, informados pelo vendedor. */
public record DadosAnuncio(String titulo, BigDecimal preco, Integer quilometragem, BigDecimal valorCompra,
                           LocalDate dataCompra, String descricao, boolean destaque) {
}
