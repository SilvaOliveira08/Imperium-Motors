package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.comum.aplicacao.ArmazenamentoService;
import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.infra.LeitorCsv;
import br.com.dataflowauto.planilhas.infra.LeitorXlsx;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.CategoriaDespesa;
import br.com.dataflowauto.vendas.dominio.Despesa;
import br.com.dataflowauto.vendas.dominio.Dinheiro;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import br.com.dataflowauto.vendas.infra.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custos dos carros do estoque. Além do lançamento manual, importa despesas de planilhas XLSX/CSV,
 * reaproveitando os leitores do módulo de planilhas e ligando cada linha ao carro pela placa.
 */
@Service
public class DespesaService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter DATA_BR_CURTA = DateTimeFormatter.ofPattern("d/M/yy");

    private final DespesaRepository despesas;
    private final AnuncioRepository anuncios;
    private final AnuncioService anuncioService;
    private final ArmazenamentoService armazenamento;
    private final LeitorXlsx leitorXlsx;
    private final LeitorCsv leitorCsv;
    private final HistoricoService historico;

    public DespesaService(DespesaRepository despesas, AnuncioRepository anuncios, AnuncioService anuncioService,
                          ArmazenamentoService armazenamento, LeitorXlsx leitorXlsx, LeitorCsv leitorCsv,
                          HistoricoService historico) {
        this.despesas = despesas;
        this.anuncios = anuncios;
        this.anuncioService = anuncioService;
        this.armazenamento = armazenamento;
        this.leitorXlsx = leitorXlsx;
        this.leitorCsv = leitorCsv;
        this.historico = historico;
    }

    @Transactional
    public Despesa adicionar(Long anuncioId, LocalDate data, CategoriaDespesa categoria, String descricao, BigDecimal valor) {
        Anuncio anuncio = anuncioService.buscar(anuncioId);
        Despesa despesa = despesas.save(new Despesa(anuncio, data, categoria, descricao, valor, Despesa.ORIGEM_MANUAL));
        historico.registrar(TipoOperacao.DESPESA, despesa.getCategoria().getDescricao() + " de R$ "
                + despesa.getValor().toPlainString().replace('.', ',') + " em " + anuncio.getTitulo());
        return despesa;
    }

    @Transactional
    public void remover(Long anuncioId, Long despesaId) {
        Despesa despesa = despesas.findById(despesaId)
                .filter(d -> d.getAnuncio().getId().equals(anuncioId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Despesa não encontrada."));
        despesas.delete(despesa);
    }

    @Transactional(readOnly = true)
    public List<Despesa> doAnuncio(Long anuncioId) {
        return despesas.findByAnuncioIdOrderByDataDescIdDesc(anuncioId);
    }

    @Transactional(readOnly = true)
    public List<Despesa> todas() {
        return despesas.findAllByOrderByDataDescIdDesc();
    }

    /**
     * Importa uma planilha com as colunas Data, Placa, Categoria, Descrição e Valor. Na planilha de
     * Controle de Despesas lê a aba "Despesas". Linhas com problema são puladas e explicadas no resultado.
     */
    @Transactional
    public ResultadoImportacao importar(MultipartFile arquivo) {
        TipoArquivo tipo = armazenamento.validar(arquivo, EnumSet.of(TipoArquivo.XLSX, TipoArquivo.CSV));
        String nome = arquivo.getOriginalFilename();
        Planilha planilha;
        try (InputStream in = arquivo.getInputStream()) {
            planilha = tipo == TipoArquivo.XLSX
                    ? leitorXlsx.lerAba(in, nome, "Despesas", "Placa")
                    : leitorCsv.ler(in, nome);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String colPlaca = coluna(planilha, "placa").orElseThrow(() -> new RegraNegocioException("A planilha precisa de uma coluna \"Placa\"."));
        String colValor = coluna(planilha, "valor").orElseThrow(() -> new RegraNegocioException("A planilha precisa de uma coluna \"Valor\"."));
        String colData = coluna(planilha, "data").orElseThrow(() -> new RegraNegocioException("A planilha precisa de uma coluna \"Data\"."));
        Optional<String> colCategoria = coluna(planilha, "categoria");
        Optional<String> colDescricao = coluna(planilha, "descricao");

        List<String> problemas = new ArrayList<>();
        // despesas que cada carro já tinha antes desta importação; reenviar a mesma planilha não duplica custos
        Map<Long, Set<String>> jaLancadas = new HashMap<>();
        int importadas = 0;
        BigDecimal total = BigDecimal.ZERO;
        int numeroLinha = 1;
        for (Linha linha : planilha.getLinhas()) {
            numeroLinha++;
            String placaTexto = linha.getValor(colPlaca);
            if (placaTexto.isBlank() && linha.getValor(colValor).isBlank()) {
                continue;
            }
            try {
                Placa placa = new Placa(placaTexto);
                Anuncio anuncio = anuncios.findByFichaVeiculoPlacaOrderByCriadoEmDesc(placa.valor()).stream().findFirst()
                        .orElseThrow(() -> new RegraNegocioException("placa " + placa.formatada() + " não está no estoque"));
                CategoriaDespesa categoria = CategoriaDespesa.interpretar(colCategoria.map(linha::getValor).orElse(null));
                String descricao = colDescricao.map(linha::getValor).orElse("");
                if (categoria == CategoriaDespesa.OUTROS && colCategoria.isPresent() && !linha.getValor(colCategoria.get()).isBlank()) {
                    // guarda o nome original da categoria para não perder a informação
                    descricao = (linha.getValor(colCategoria.get()) + " " + descricao).trim();
                }
                Despesa despesa = new Despesa(anuncio, data(linha.getValor(colData)), categoria, descricao,
                        Dinheiro.interpretar(linha.getValor(colValor)), "Planilha: " + nome + ", linha " + numeroLinha);
                Set<String> doCarro = jaLancadas.computeIfAbsent(anuncio.getId(), anuncioId ->
                        despesas.findByAnuncioIdOrderByDataDescIdDesc(anuncioId).stream()
                                .map(Despesa::chaveLancamento)
                                .collect(Collectors.toSet()));
                if (doCarro.contains(despesa.chaveLancamento())) {
                    problemas.add("Linha " + numeroLinha + ": já estava lançada no carro " + placa.formatada()
                            + ", foi ignorada para não duplicar o custo");
                    continue;
                }
                despesas.save(despesa);
                importadas++;
                total = total.add(despesa.getValor());
            } catch (RegraNegocioException e) {
                problemas.add("Linha " + numeroLinha + ": " + primeiraMinuscula(e.getMessage()));
            }
        }
        historico.registrar(TipoOperacao.DESPESA, "Importadas " + importadas + " despesa(s) de " + nome
                + (problemas.isEmpty() ? "" : " (" + problemas.size() + " linha(s) ignorada(s))"));
        return new ResultadoImportacao(importadas, total, problemas);
    }

    static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("data vazia");
        }
        String t = texto.trim();
        for (DateTimeFormatter f : List.of(DATA_BR, DateTimeFormatter.ISO_LOCAL_DATE, DATA_BR_CURTA)) {
            try {
                return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t, f);
            } catch (DateTimeParseException e) {
                // tenta o próximo formato
            }
        }
        throw new RegraNegocioException("data inválida \"" + t + "\"");
    }

    private static Optional<String> coluna(Planilha planilha, String nome) {
        return planilha.getNomesColunas().stream()
                .filter(c -> CampoTecnico.normalizar(c).equals(nome))
                .findFirst();
    }

    private static String primeiraMinuscula(String texto) {
        return texto == null || texto.isEmpty() ? "" : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    public record ResultadoImportacao(int importadas, BigDecimal total, List<String> problemas) {
    }
}
