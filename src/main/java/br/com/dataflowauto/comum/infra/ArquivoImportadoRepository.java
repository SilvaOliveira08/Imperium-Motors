package br.com.dataflowauto.comum.infra;

import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArquivoImportadoRepository extends JpaRepository<ArquivoImportado, Long> {

    List<ArquivoImportado> findByTipoInOrderByImportadoEmDesc(Collection<TipoArquivo> tipos);
}
