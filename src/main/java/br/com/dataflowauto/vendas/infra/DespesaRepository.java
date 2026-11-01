package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.vendas.dominio.Despesa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findByAnuncioIdOrderByDataDescIdDesc(Long anuncioId);

    @EntityGraph(attributePaths = "anuncio")
    List<Despesa> findAllByOrderByDataDescIdDesc();
}
