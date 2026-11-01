package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    List<Proposta> findAllByOrderByCriadaEmDesc();

    List<Proposta> findByStatusOrderByCriadaEmDesc(StatusProposta status);

    List<Proposta> findByAnuncioIdOrderByCriadaEmDesc(Long anuncioId);

    long countByStatus(StatusProposta status);
}
