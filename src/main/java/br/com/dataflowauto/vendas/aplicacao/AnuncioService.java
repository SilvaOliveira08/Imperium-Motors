package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.FiltroLoja;
import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.dominio.Rentabilidade;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import br.com.dataflowauto.vendas.dominio.Venda;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import br.com.dataflowauto.vendas.infra.ArmazenamentoFotos;
import br.com.dataflowauto.vendas.infra.DespesaRepository;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Casos de uso do estoque da loja: anunciar a partir da ficha, fotos, situação e venda. */
@Service
public class AnuncioService {

    private static final EnumSet<StatusAnuncio> NA_LOJA = EnumSet.of(StatusAnuncio.DISPONIVEL, StatusAnuncio.RESERVADO);

    private final AnuncioRepository anuncios;
    private final DespesaRepository despesas;
    private final PropostaRepository propostas;
    private final FichaService fichas;
    private final ArmazenamentoFotos fotos;
    private final HistoricoService historico;

    public AnuncioService(AnuncioRepository anuncios, DespesaRepository despesas, PropostaRepository propostas,
                          FichaService fichas, ArmazenamentoFotos fotos, HistoricoService historico) {
        this.anuncios = anuncios;
        this.despesas = despesas;
        this.propostas = propostas;
        this.fichas = fichas;
        this.fotos = fotos;
        this.historico = historico;
    }

    @Transactional
    public Anuncio criar(Long fichaId, DadosAnuncio dados, List<MultipartFile> novasFotos) {
        FichaTecnica ficha = fichas.buscar(fichaId);
        // por veículo, não por ficha: a mesma placa pode ter mais de uma ficha
        if (anuncios.existsByVeiculoEmAndamentoId(ficha.getVeiculo().getId())) {
            throw jaAnunciado(ficha.getVeiculo());
        }
        Anuncio anuncio = new Anuncio(ficha, dados);
        guardarFotos(anuncio, novasFotos);
        Anuncio salvo;
        try {
            // flush aqui para o clique duplo cair no catch, e não no commit
            salvo = anuncios.saveAndFlush(anuncio);
        } catch (DataIntegrityViolationException e) {
            throw jaAnunciado(ficha.getVeiculo());
        }
        historico.registrar(TipoOperacao.ANUNCIO, "Anunciado " + salvo.getTitulo() + " (" + salvo.getVeiculo().getPlacaFormatada() + ")");
        return salvo;
    }

    @Transactional
    public Anuncio atualizar(Long id, DadosAnuncio dados) {
        Anuncio anuncio = buscar(id);
        anuncio.atualizar(dados);
        return anuncios.save(anuncio);
    }

    @Transactional
    public void adicionarFotos(Long id, List<MultipartFile> novasFotos) {
        Anuncio anuncio = buscar(id);
        if (guardarFotos(anuncio, novasFotos) == 0) {
            throw new RegraNegocioException("Escolha ao menos uma foto.");
        }
        anuncios.save(anuncio);
    }

    @Transactional
    public void removerFoto(Long id, int indice) {
        Anuncio anuncio = buscar(id);
        String arquivo = anuncio.removerFoto(indice);
        anuncios.save(anuncio);
        fotos.excluir(arquivo);
    }

    @Transactional
    public void definirCapa(Long id, int indice) {
        Anuncio anuncio = buscar(id);
        anuncio.definirCapa(indice);
        anuncios.save(anuncio);
    }

    @Transactional
    public Anuncio alterarStatus(Long id, StatusAnuncio status) {
        Anuncio anuncio = buscar(id);
        anuncio.alterarStatus(status);
        historico.registrar(TipoOperacao.ANUNCIO, anuncio.getTitulo() + ": " + status.getDescricao().toLowerCase());
        return anuncios.save(anuncio);
    }

    /** Fecha o negócio. Se veio de um interessado, ele fica como "virou venda" e os demais abertos são descartados. */
    @Transactional
    public Anuncio registrarVenda(Long id, Venda venda, Long propostaId) {
        Anuncio anuncio = buscar(id);
        anuncio.registrarVenda(venda);
        for (Proposta p : propostas.findByAnuncioIdOrderByCriadaEmDesc(id)) {
            if (Objects.equals(p.getId(), propostaId)) {
                p.alterarStatus(StatusProposta.CONVERTIDA);
            } else if (p.getStatus().isAberta()) {
                p.alterarStatus(StatusProposta.DESCARTADA);
            }
        }
        historico.registrar(TipoOperacao.VENDA, anuncio.getTitulo() + " (" + anuncio.getVeiculo().getPlacaFormatada()
                + ") vendido para " + venda.getCompradorNome());
        return anuncios.save(anuncio);
    }

    @Transactional
    public void desfazerVenda(Long id) {
        Anuncio anuncio = buscar(id);
        if (anuncio.estaVendido() && anuncios.existsByVeiculoEmAndamentoId(anuncio.getVeiculo().getId())) {
            throw new RegraNegocioException("A placa " + anuncio.getVeiculo().getPlacaFormatada()
                    + " já foi anunciada de novo. Exclua o anúncio novo antes de desfazer esta venda.");
        }
        anuncio.desfazerVenda();
        anuncios.save(anuncio);
        historico.registrar(TipoOperacao.VENDA, "Venda desfeita: " + anuncio.getTitulo());
    }

    @Transactional
    public void excluir(Long id) {
        Anuncio anuncio = buscar(id);
        if (anuncio.estaVendido()) {
            throw new RegraNegocioException("Um carro vendido não pode ser excluído: ele faz parte do histórico de vendas.");
        }
        List<String> arquivos = anuncio.getFotos();
        anuncios.delete(anuncio);
        arquivos.forEach(fotos::excluir);
        historico.registrar(TipoOperacao.ANUNCIO, "Anúncio excluído: " + anuncio.getTitulo());
    }

    @Transactional(readOnly = true)
    public Anuncio buscar(Long id) {
        return anuncios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Anúncio " + id + " não encontrado."));
    }

    /** Página pública: só aparece o que está na loja. */
    @Transactional(readOnly = true)
    public Anuncio buscarNaLoja(Long id) {
        Anuncio anuncio = buscar(id);
        if (!anuncio.estaNaLoja()) {
            throw new RecursoNaoEncontradoException("Este carro não está mais disponível.");
        }
        return anuncio;
    }

    @Transactional(readOnly = true)
    public List<Anuncio> listar() {
        return anuncios.findAllByOrderByAtualizadoEmDesc();
    }

    /**
     * Tudo o que a página do catálogo precisa, a partir de uma única leitura do estoque
     * (antes eram três: vitrine, total e opções dos filtros).
     */
    @Transactional(readOnly = true)
    public Catalogo catalogo(FiltroLoja filtro) {
        List<Anuncio> publicados = naLoja();
        return new Catalogo(publicados, filtro.aplicar(publicados), opcoesFiltro(publicados));
    }

    @Transactional(readOnly = true)
    public List<Anuncio> naLoja() {
        return anuncios.findByStatusIn(NA_LOJA).stream().filter(Anuncio::estaNaLoja).toList();
    }

    @Transactional(readOnly = true)
    public List<Anuncio> semelhantes(Anuncio base, int limite) {
        return naLoja().stream()
                .filter(a -> !a.getId().equals(base.getId()))
                .sorted(Comparator.comparing((Anuncio a) -> !a.getMarca().equalsIgnoreCase(base.getMarca()))
                        .thenComparing(a -> a.getPreco().subtract(base.getPreco()).abs()))
                .limit(limite)
                .toList();
    }

    /** Opções dos filtros da loja, montadas a partir dos carros publicados. */
    private static OpcoesFiltro opcoesFiltro(List<Anuncio> publicados) {
        return new OpcoesFiltro(distintos(publicados.stream().map(Anuncio::getMarca).toList()),
                distintos(publicados.stream().map(Anuncio::getCombustivel).toList()),
                distintos(publicados.stream().map(Anuncio::getTipoCambio).toList()));
    }

    /** Fichas concluídas que ainda não têm anúncio em andamento. */
    @Transactional(readOnly = true)
    public List<FichaTecnica> fichasProntasParaAnunciar() {
        return fichas.listar().stream()
                .filter(f -> f.getStatus() == StatusFicha.CONCLUIDA)
                .filter(f -> !anuncios.existsByVeiculoEmAndamentoId(f.getVeiculo().getId()))
                .toList();
    }

    private static RegraNegocioException jaAnunciado(Veiculo veiculo) {
        return new RegraNegocioException("A placa " + veiculo.getPlacaFormatada() + " já tem um anúncio em andamento.");
    }

    @Transactional(readOnly = true)
    public Rentabilidade rentabilidade(Long id) {
        return Rentabilidade.calcular(buscar(id), despesas.findByAnuncioIdOrderByDataDescIdDesc(id));
    }

    private int guardarFotos(Anuncio anuncio, List<MultipartFile> novasFotos) {
        if (novasFotos == null) {
            return 0;
        }
        int guardadas = 0;
        for (MultipartFile foto : novasFotos) {
            if (foto == null || foto.isEmpty()) {
                continue;
            }
            if (anuncio.getFotos().size() >= Anuncio.MAXIMO_FOTOS) {
                throw new RegraNegocioException("Cada anúncio pode ter no máximo " + Anuncio.MAXIMO_FOTOS + " fotos.");
            }
            anuncio.adicionarFoto(fotos.guardar(foto));
            guardadas++;
        }
        return guardadas;
    }

    private static List<String> distintos(List<String> valores) {
        TreeSet<String> conjunto = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        valores.stream().filter(v -> v != null && !v.isBlank()).forEach(conjunto::add);
        return List.copyOf(conjunto);
    }

    public record OpcoesFiltro(List<String> marcas, List<String> combustiveis, List<String> cambios) {
    }

    /** Página do catálogo: os carros na loja, o resultado do filtro e as opções dos filtros. */
    public record Catalogo(List<Anuncio> naLoja, List<Anuncio> resultado, OpcoesFiltro opcoes) {
    }
}
