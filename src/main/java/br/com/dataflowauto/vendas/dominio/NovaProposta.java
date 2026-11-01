package br.com.dataflowauto.vendas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O que o visitante preenche no formulário de contato da página do carro. */
public record NovaProposta(TipoProposta tipo, String nome, String telefone, String email, String mensagem,
                           BigDecimal valorOferecido, LocalDate dataPreferida) {
}
