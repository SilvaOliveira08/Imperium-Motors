package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.vendas.dominio.Proposta;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento publicado quando um visitante envia o formulário de contato do site. Leva uma cópia dos dados
 * (e não a entidade), porque quem escuta roda depois do commit, em outra thread, sem sessão do JPA.
 */
public record ContatoRecebido(Long propostaId, String tipo, String carro, String nome, String telefone,
                              String email, String mensagem, BigDecimal valorOferecido, LocalDate dataPreferida) {

    public static ContatoRecebido de(Proposta proposta) {
        return new ContatoRecebido(proposta.getId(), proposta.getTipo().getDescricao(),
                proposta.getAnuncio().getTitulo(), proposta.getNome(), proposta.getTelefone(), proposta.getEmail(),
                proposta.getMensagem(), proposta.getValorOferecido(), proposta.getDataPreferida());
    }
}
