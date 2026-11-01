package br.com.dataflowauto.fichas.aplicacao;

import br.com.dataflowauto.comum.aplicacao.ArmazenamentoService;
import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.FonteDadosVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteIndisponivelException;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import br.com.dataflowauto.fichas.infra.ExtratorTextoDocumento;
import br.com.dataflowauto.fichas.infra.FichaTecnicaRepository;
import br.com.dataflowauto.fichas.infra.GeradorFichaPdf;
import br.com.dataflowauto.fichas.infra.VeiculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casos de uso das fichas técnicas. O preenchimento percorre as fontes na ordem
 * de prioridade (documento, API, base de demonstração); cada valor fica marcado com sua origem.
 */
@Service
public class FichaService {

    private static final Set<TipoArquivo> DOCUMENTOS_ACEITOS = EnumSet.of(TipoArquivo.PDF, TipoArquivo.TXT);

    private final FichaTecnicaRepository fichas;
    private final VeiculoRepository veiculos;
    private final List<FonteDadosVeiculo> fontes;
    private final ArmazenamentoService armazenamento;
    private final ExtratorTextoDocumento extrator;
    private final GeradorFichaPdf geradorPdf;
    private final HistoricoService historico;
    private final List<VerificadorUsoFicha> verificadoresUso;
    private final TransactionTemplate transacao;

    public FichaService(FichaTecnicaRepository fichas, VeiculoRepository veiculos, List<FonteDadosVeiculo> fontes,
                        ArmazenamentoService armazenamento, ExtratorTextoDocumento extrator,
                        GeradorFichaPdf geradorPdf, HistoricoService historico,
                        List<VerificadorUsoFicha> verificadoresUso, PlatformTransactionManager gerenciadorTransacoes) {
        this.fichas = fichas;
        this.veiculos = veiculos;
        this.fontes = fontes;
        this.armazenamento = armazenamento;
        this.extrator = extrator;
        this.geradorPdf = geradorPdf;
        this.historico = historico;
        this.verificadoresUso = verificadoresUso;
        this.transacao = new TransactionTemplate(gerenciadorTransacoes);
    }

    /**
     * Sem {@code @Transactional} de propósito: a consulta às fontes pode levar segundos (API externa)
     * e não pode segurar uma conexão com o banco. Primeiro guarda o documento, depois consulta as fontes
     * e só então grava a ficha numa transação curta.
     */
    public FichaTecnica criar(NovaFichaComando comando) {
        Placa placa = new Placa(comando.placa());

        String textoDocumento = null;
        ArquivoImportado arquivo = null;
        if (comando.possuiDocumento()) {
            TipoArquivo tipo = armazenamento.validar(comando.documento(), DOCUMENTOS_ACEITOS);
            try {
                textoDocumento = extrator.extrair(comando.documento().getBytes(), tipo);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            arquivo = armazenamento.guardar(comando.documento(), DOCUMENTOS_ACEITOS);
        }

        List<RespostaFonte> respostas = buscarNasFontes(new ConsultaVeiculo(placa, textoDocumento,
                arquivo == null ? null : arquivo.getNomeOriginal(), comando.valoresInformados().get(CampoTecnico.RENAVAM)));

        ArquivoImportado documento = arquivo;
        return transacao.execute(status -> {
            Veiculo veiculo = veiculos.findByPlaca(placa.valor()).orElseGet(() -> veiculos.save(new Veiculo(placa)));
            FichaTecnica ficha = new FichaTecnica(veiculo);
            comando.valoresInformados().forEach((campo, valor) ->
                    ficha.registrarDado(campo, valor, OrigemDado.USUARIO, null));
            if (documento != null) {
                ficha.vincularArquivo(documento);
            }
            ficha.registrarFontesConsultadas(aplicarRespostas(ficha, respostas));

            FichaTecnica salva = fichas.save(ficha);
            historico.registrar(TipoOperacao.FICHA_CRIADA, "Placa " + placa.formatada() + ": "
                    + salva.getCampos().stream().filter(c -> c.possuiValor()).count() + " campos preenchidos");
            return salva;
        });
    }

    /**
     * Refaz a consulta só pela placa (API e bases), preenchendo apenas campos ainda vazios.
     * Serve para atualizar multas, débitos e restrições, que mudam com o tempo. O que chegar
     * entra como "a confirmar", então uma ficha concluída volta para revisão se algo novo aparecer.
     * Como em {@link #criar}, a consulta acontece fora da transação.
     */
    public int reconsultar(Long id) {
        FichaTecnica atual = buscar(id);
        List<RespostaFonte> respostas = buscarNasFontes(new ConsultaVeiculo(atual.getVeiculo().getPlaca(), null, null,
                atual.campo(CampoTecnico.RENAVAM).getValor()));

        Integer novos = transacao.execute(status -> {
            // relê a ficha: alguém pode ter revisado enquanto as fontes respondiam
            FichaTecnica ficha = buscar(id);
            long antes = ficha.getCampos().stream().filter(c -> c.possuiValor()).count();
            ficha.registrarFontesConsultadas(aplicarRespostas(ficha, respostas));
            int encontrados = (int) (ficha.getCampos().stream().filter(c -> c.possuiValor()).count() - antes);
            fichas.save(ficha);
            historico.registrar(TipoOperacao.FICHA_REVISADA, "Placa " + ficha.getVeiculo().getPlacaFormatada()
                    + ": nova consulta, " + encontrados + " campo(s) novo(s)");
            return encontrados;
        });
        return novos == null ? 0 : novos;
    }

    @Transactional(readOnly = true)
    public FichaTecnica buscar(Long id) {
        return fichas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ficha " + id + " não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<FichaTecnica> listar() {
        return fichas.findAllByOrderByAtualizadaEmDesc();
    }

    /**
     * Aplica a revisão do usuário: valores editados viram dados do usuário (confirmados)
     * e os campos marcados são confirmados. Se pedido, tenta concluir a ficha.
     */
    @Transactional
    public FichaTecnica revisar(Long id, Map<CampoTecnico, String> valores, Set<CampoTecnico> confirmados, boolean concluir) {
        FichaTecnica ficha = buscar(id);
        valores.forEach(ficha::editar);
        confirmados.stream()
                .filter(c -> ficha.campo(c).possuiValor())
                .forEach(ficha::confirmar);
        if (concluir) {
            ficha.concluir();
        }
        historico.registrar(TipoOperacao.FICHA_REVISADA, "Placa " + ficha.getVeiculo().getPlacaFormatada()
                + ": " + ficha.getStatus().getDescricao().toLowerCase()
                + ", " + ficha.camposPendentes().size() + " pendências");
        return fichas.save(ficha);
    }

    @Transactional
    public void excluir(Long id) {
        FichaTecnica ficha = buscar(id);
        for (VerificadorUsoFicha verificador : verificadoresUso) {
            verificador.impedimentoParaExcluir(ficha).ifPresent(motivo -> {
                throw new RegraNegocioException(motivo);
            });
        }
        fichas.delete(ficha);
    }

    @Transactional
    public void gerarPdf(Long id, OutputStream destino) {
        FichaTecnica ficha = buscar(id);
        geradorPdf.gerar(ficha, destino, false);
        historico.registrar(TipoOperacao.FICHA_PDF, "Placa " + ficha.getVeiculo().getPlacaFormatada()
                + " (" + ficha.getStatus().getDescricao().toLowerCase() + ")");
    }

    /** O que uma fonte respondeu; {@code dados} é nulo quando ela não conseguiu consultar. */
    private record RespostaFonte(FonteDadosVeiculo fonte, String detalhe, Map<CampoTecnico, String> dados) {
    }

    /** Pergunta a todas as fontes, na ordem de prioridade. Pode demorar: nunca chamar dentro de transação. */
    private List<RespostaFonte> buscarNasFontes(ConsultaVeiculo consulta) {
        List<RespostaFonte> respostas = new ArrayList<>();
        for (FonteDadosVeiculo fonte : fontes) {
            if (!fonte.podeAtender(consulta)) {
                continue;
            }
            String detalhe = fonte.origem() == OrigemDado.DOCUMENTO && consulta.nomeDocumento() != null
                    ? fonte.nome() + ": " + consulta.nomeDocumento()
                    : fonte.nome();
            Map<CampoTecnico, String> dados;
            try {
                dados = fonte.buscar(consulta);
            } catch (FonteIndisponivelException e) {
                dados = null;
            }
            respostas.add(new RespostaFonte(fonte, detalhe, dados));
        }
        return respostas;
    }

    /** Aplica as respostas na ordem de prioridade; a primeira que trouxer um campo vence. */
    private static List<String> aplicarRespostas(FichaTecnica ficha, List<RespostaFonte> respostas) {
        List<String> consultadas = new ArrayList<>();
        for (RespostaFonte resposta : respostas) {
            if (resposta.dados() == null) {
                // não é "0 encontrados": a consulta não aconteceu e a ficha precisa dizer isso
                consultadas.add(FichaTecnica.fonteIndisponivel(resposta.detalhe()));
                continue;
            }
            long usados = resposta.dados().entrySet().stream()
                    .filter(e -> ficha.registrarDado(e.getKey(), e.getValue(), resposta.fonte().origem(), resposta.detalhe()))
                    .count();
            consultadas.add(resposta.detalhe() + " (" + resposta.dados().size() + " encontrados, " + usados + " usados)");
        }
        return consultadas;
    }

    /** PDF entregue ao cliente pela loja: só campos públicos (sem chassi, proprietários, multas e débitos). */
    @Transactional(readOnly = true)
    public void gerarPdfPublico(Long id, OutputStream destino) {
        geradorPdf.gerar(buscar(id), destino, true);
    }
}
