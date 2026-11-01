package br.com.dataflowauto.marketing.infra;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.marketing.dominio.Publicacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicacaoRepository extends JpaRepository<Publicacao, Long> {

    List<Publicacao> findByAnuncioId(Long anuncioId);

    Optional<Publicacao> findByAnuncioIdAndCanal(Long anuncioId, CanalDivulgacao canal);

    boolean existsByAnuncioId(Long anuncioId);
}
