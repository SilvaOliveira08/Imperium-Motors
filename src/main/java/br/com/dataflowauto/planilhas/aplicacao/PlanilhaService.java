package br.com.dataflowauto.planilhas.aplicacao;

import br.com.dataflowauto.comum.aplicacao.ArmazenamentoService;
import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.ArquivoInvalidoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.comum.infra.ArquivoImportadoRepository;
import br.com.dataflowauto.planilhas.dominio.AnalisadorQualidade;
import br.com.dataflowauto.planilhas.dominio.ConsolidadorPlanilhas;
import br.com.dataflowauto.planilhas.dominio.EscritorPlanilha;
import br.com.dataflowauto.planilhas.dominio.LeitorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.dominio.RelatorioQualidade;
import br.com.dataflowauto.planilhas.dominio.TipoJuncao;
import br.com.dataflowauto.planilhas.dominio.regras.RegraTransformacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casos de uso do módulo de planilhas: importar, carregar, organizar, combinar e exportar.
 * Escolhe o leitor/escritor pelo formato (as implementações são injetadas pelo Spring).
 */
@Service
public class PlanilhaService {

    private static final Set<TipoArquivo> FORMATOS_ACEITOS = EnumSet.of(TipoArquivo.XLSX, TipoArquivo.CSV);
    /** Poucas: cada uma pode ter até {@link Planilha#MAXIMO_LINHAS} linhas. */
    private static final int PLANILHAS_GUARDADAS = 4;

    private final ArmazenamentoService armazenamento;
    private final ArquivoImportadoRepository arquivos;
    private final HistoricoService historico;
    private final List<LeitorPlanilha> leitores;
    private final List<EscritorPlanilha> escritores;
    private final ConsolidadorPlanilhas consolidador = new ConsolidadorPlanilhas();
    private final AnalisadorQualidade analisador = new AnalisadorQualidade();
    // em ordem de acesso: a menos usada sai quando passa do limite
    private final Map<Long, Planilha> lidas = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Planilha> maisAntiga) {
            return size() > PLANILHAS_GUARDADAS;
        }
    };

    public PlanilhaService(ArmazenamentoService armazenamento, ArquivoImportadoRepository arquivos,
                           HistoricoService historico, List<LeitorPlanilha> leitores,
                           List<EscritorPlanilha> escritores) {
        this.armazenamento = armazenamento;
        this.arquivos = arquivos;
        this.historico = historico;
        this.leitores = leitores;
        this.escritores = escritores;
    }

    /** Valida, lê e guarda os arquivos. Um arquivo que não pode ser lido não é salvo. */
    @Transactional
    public List<ArquivoImportado> importar(List<MultipartFile> enviados) {
        List<MultipartFile> validos = enviados == null ? List.of()
                : enviados.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (validos.isEmpty()) {
            throw new ArquivoInvalidoException("Selecione pelo menos um arquivo XLSX ou CSV.");
        }
        List<ArquivoImportado> importados = new ArrayList<>();
        for (MultipartFile arquivo : validos) {
            TipoArquivo tipo = armazenamento.validar(arquivo, FORMATOS_ACEITOS);
            Planilha lida;
            try (InputStream in = arquivo.getInputStream()) {
                lida = leitorPara(tipo).ler(in, arquivo.getOriginalFilename());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ArquivoImportado salvo = armazenamento.guardar(arquivo, FORMATOS_ACEITOS);
            importados.add(salvo);
            historico.registrar(TipoOperacao.IMPORTACAO, salvo.getNomeOriginal() + " (" + lida.totalLinhas()
                    + " linhas, " + lida.totalColunas() + " colunas)");
        }
        return importados;
    }

    @Transactional(readOnly = true)
    public List<ArquivoImportado> listarArquivos() {
        return arquivos.findByTipoInOrderByImportadoEmDesc(FORMATOS_ACEITOS);
    }

    /**
     * Lê a planilha de um arquivo importado. As últimas lidas ficam guardadas: o arquivo nunca muda
     * depois de importado e a {@link Planilha} é imutável, então navegar pelas páginas da
     * pré-visualização (ou combinar de novo com a mesma planilha) não relê o arquivo do disco.
     */
    @Transactional(readOnly = true)
    public Planilha carregar(Long arquivoId) {
        synchronized (lidas) {
            Planilha guardada = lidas.get(arquivoId);
            if (guardada != null) {
                return guardada;
            }
        }
        Planilha lida = lerDoDisco(arquivoId);
        synchronized (lidas) {
            lidas.put(arquivoId, lida);
        }
        return lida;
    }

    private Planilha lerDoDisco(Long arquivoId) {
        ArquivoImportado arquivo = armazenamento.buscar(arquivoId);
        if (!arquivo.getTipo().ehPlanilha()) {
            throw new RegraNegocioException("O arquivo \"" + arquivo.getNomeOriginal() + "\" não é uma planilha.");
        }
        try (InputStream in = armazenamento.abrir(arquivo)) {
            return leitorPara(arquivo.getTipo()).ler(in, nomeSemExtensao(arquivo.getNomeOriginal()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public RelatorioQualidade analisar(Planilha planilha) {
        return analisador.analisar(planilha);
    }

    /** Aplica as regras em sequência e registra no histórico. Cada regra recebe o resultado da anterior. */
    public Planilha organizar(Planilha planilha, List<RegraTransformacao> regras) {
        Planilha resultado = aplicarRegras(planilha, regras);
        if (!regras.isEmpty()) {
            historico.registrar(TipoOperacao.ORGANIZACAO, planilha.getNome() + ": "
                    + String.join("; ", regras.stream().map(RegraTransformacao::getDescricao).toList()));
        }
        return resultado;
    }

    public Planilha combinar(Planilha primeira, Planilha segunda, String chavePrimeira, String chaveSegunda,
                             TipoJuncao tipo) {
        Planilha resultado = juntar(primeira, segunda, chavePrimeira, chaveSegunda, tipo);
        historico.registrar(TipoOperacao.COMBINACAO, primeira.getNome() + " (" + chavePrimeira + ") com "
                + segunda.getNome() + " (" + chaveSegunda + "), " + tipo.getDescricao().toLowerCase()
                + ": " + resultado.totalLinhas() + " linhas");
        return resultado;
    }

    /** Só aplica as regras, sem histórico: usado para refazer etapas ao desfazer. */
    public Planilha aplicarRegras(Planilha planilha, List<RegraTransformacao> regras) {
        Planilha resultado = planilha;
        for (RegraTransformacao regra : regras) {
            resultado = regra.aplicar(resultado);
        }
        return resultado;
    }

    /** Só combina, sem histórico: usado para refazer etapas ao desfazer. */
    public Planilha juntar(Planilha primeira, Planilha segunda, String chavePrimeira, String chaveSegunda,
                           TipoJuncao tipo) {
        return consolidador.combinar(primeira, segunda, chavePrimeira, chaveSegunda, tipo);
    }

    public EscritorPlanilha escritorPara(TipoArquivo formato) {
        return escritores.stream().filter(e -> e.formato() == formato).findFirst()
                .orElseThrow(() -> new RegraNegocioException("Exportação em " + formato + " não disponível."));
    }

    public void exportar(Planilha planilha, TipoArquivo formato, OutputStream destino) {
        escritorPara(formato).escrever(planilha, destino);
        historico.registrar(TipoOperacao.EXPORTACAO, planilha.getNome() + " em " + formato
                + " (" + planilha.totalLinhas() + " linhas)");
    }

    private LeitorPlanilha leitorPara(TipoArquivo tipo) {
        return leitores.stream().filter(l -> l.suporta(tipo)).findFirst()
                .orElseThrow(() -> new ArquivoInvalidoException("Não há leitor para arquivos " + tipo + "."));
    }

    public static String nomeSemExtensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto > 0 ? nome.substring(0, ponto) : nome;
    }
}
