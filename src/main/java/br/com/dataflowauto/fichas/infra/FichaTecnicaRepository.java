package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    List<FichaTecnica> findAllByOrderByAtualizadaEmDesc();
}
