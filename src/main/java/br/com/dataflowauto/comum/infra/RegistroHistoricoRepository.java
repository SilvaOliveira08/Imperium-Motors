package br.com.dataflowauto.comum.infra;

import br.com.dataflowauto.comum.dominio.RegistroHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroHistoricoRepository extends JpaRepository<RegistroHistorico, Long> {

    List<RegistroHistorico> findTop10ByOrderByRealizadoEmDesc();

    List<RegistroHistorico> findAllByOrderByRealizadoEmDesc();
}
