package br.com.dataflowauto.comum.aplicacao;

import br.com.dataflowauto.comum.dominio.RegistroHistorico;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.comum.infra.RegistroHistoricoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoricoService {

    private final RegistroHistoricoRepository repositorio;

    public HistoricoService(RegistroHistoricoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void registrar(TipoOperacao operacao, String detalhes) {
        repositorio.save(new RegistroHistorico(operacao, detalhes));
    }

    @Transactional(readOnly = true)
    public List<RegistroHistorico> recentes() {
        return repositorio.findTop10ByOrderByRealizadoEmDesc();
    }

    @Transactional(readOnly = true)
    public List<RegistroHistorico> todos() {
        return repositorio.findAllByOrderByRealizadoEmDesc();
    }
}
