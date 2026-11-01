package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.NovaProposta;
import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Interessados que entram em contato pela loja e o acompanhamento deles pela equipe. */
@Service
public class PropostaService {

    private final PropostaRepository propostas;
    private final AnuncioService anuncios;
    private final HistoricoService historico;

    public PropostaService(PropostaRepository propostas, AnuncioService anuncios, HistoricoService historico) {
        this.propostas = propostas;
        this.anuncios = anuncios;
        this.historico = historico;
    }

    @Transactional
    public Proposta registrar(Long anuncioId, NovaProposta dados) {
        return registrar(anuncioId, dados, null);
    }

    /** Registra o contato guardando o canal de divulgação de onde o visitante chegou, se houver. */
    @Transactional
    public Proposta registrar(Long anuncioId, NovaProposta dados, CanalDivulgacao origem) {
        Anuncio anuncio = anuncios.buscarNaLoja(anuncioId);
        Proposta nova = new Proposta(anuncio, dados);
        nova.registrarOrigem(origem);
        Proposta proposta = propostas.save(nova);
        historico.registrar(TipoOperacao.PROPOSTA, proposta.getTipo().getDescricao() + " de " + proposta.getNome()
                + " para " + anuncio.getTitulo());
        return proposta;
    }

    @Transactional
    public void alterarStatus(Long id, StatusProposta status) {
        Proposta proposta = propostas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contato " + id + " não encontrado."));
        proposta.alterarStatus(status);
        propostas.save(proposta);
    }

    @Transactional(readOnly = true)
    public List<Proposta> listar(StatusProposta status) {
        return status == null ? propostas.findAllByOrderByCriadaEmDesc() : propostas.findByStatusOrderByCriadaEmDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Proposta> doAnuncio(Long anuncioId) {
        return propostas.findByAnuncioIdOrderByCriadaEmDesc(anuncioId);
    }

    @Transactional(readOnly = true)
    public long novas() {
        return propostas.countByStatus(StatusProposta.NOVA);
    }
}
