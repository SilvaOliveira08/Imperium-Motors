package br.com.dataflowauto.vendas.infra;

import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnuncioRepository extends JpaRepository<Anuncio, Long> {

    List<Anuncio> findAllByOrderByAtualizadoEmDesc();

    List<Anuncio> findByStatusIn(Collection<StatusAnuncio> status);

    List<Anuncio> findByFichaId(Long fichaId);

    /** Há anúncio em andamento (não vendido) para o veículo, em qualquer uma das fichas dele? */
    boolean existsByVeiculoEmAndamentoId(Long veiculoId);

    List<Anuncio> findByFichaVeiculoPlacaOrderByCriadoEmDesc(String placa);
}
