package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.Despesa;
import br.com.dataflowauto.vendas.dominio.Rentabilidade;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import br.com.dataflowauto.vendas.infra.DespesaRepository;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Números do negócio para o painel e as planilhas de exportação (estoque, despesas e vendas).
 * As exportações usam a mesma {@link Planilha} do módulo de planilhas, então podem ser
 * importadas de volta lá para organizar e combinar com outros arquivos.
 */
@Service
public class PainelService {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AnuncioRepository anuncios;
    private final DespesaRepository despesas;
    private final PropostaRepository propostas;

    public PainelService(AnuncioRepository anuncios, DespesaRepository despesas, PropostaRepository propostas) {
        this.anuncios = anuncios;
        this.despesas = despesas;
        this.propostas = propostas;
    }

    @Transactional(readOnly = true)
    public Indicadores indicadores() {
        List<Anuncio> todos = anuncios.findAll();
        Map<Long, List<Despesa>> porAnuncio = despesasPorAnuncio();
        List<Anuncio> estoque = todos.stream().filter(a -> !a.estaVendido()).toList();
        List<Anuncio> vendidos = todos.stream().filter(Anuncio::estaVendido).toList();

        BigDecimal valorEstoque = soma(estoque.stream().map(Anuncio::getPreco).toList());
        BigDecimal custoEstoque = soma(estoque.stream().map(a -> rentabilidade(a, porAnuncio).custoTotal()).toList());

        YearMonth mesAtual = YearMonth.now();
        List<Anuncio> vendidosNoMes = vendidos.stream().filter(a -> YearMonth.from(a.getVenda().getData()).equals(mesAtual)).toList();
        BigDecimal faturamentoMes = soma(vendidosNoMes.stream().map(a -> a.getVenda().getValor()).toList());
        BigDecimal lucroMes = soma(vendidosNoMes.stream().map(a -> rentabilidade(a, porAnuncio).lucro()).toList());
        BigDecimal lucroTotal = soma(vendidos.stream().map(a -> rentabilidade(a, porAnuncio).lucro()).toList());
        BigDecimal ticketMedio = vendidos.isEmpty() ? BigDecimal.ZERO
                : soma(vendidos.stream().map(a -> a.getVenda().getValor()).toList())
                .divide(BigDecimal.valueOf(vendidos.size()), 2, RoundingMode.HALF_UP);
        long diasMedios = Math.round(vendidos.stream().filter(a -> a.getDataCompra() != null)
                .mapToLong(a -> ChronoUnit.DAYS.between(a.getDataCompra(), a.getVenda().getData()))
                .average().orElse(0));

        List<VendaMes> ultimosMeses = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = mesAtual.minusMonths(i);
            List<Anuncio> doMes = vendidos.stream().filter(a -> YearMonth.from(a.getVenda().getData()).equals(mes)).toList();
            String rotulo = mes.getMonth().getDisplayName(TextStyle.SHORT, BR).replace(".", "") + "/" + String.valueOf(mes.getYear()).substring(2);
            ultimosMeses.add(new VendaMes(rotulo, doMes.size(), soma(doMes.stream().map(a -> a.getVenda().getValor()).toList())));
        }

        List<Anuncio> ultimasVendas = vendidos.stream()
                .sorted(Comparator.comparing((Anuncio a) -> a.getVenda().getData()).reversed())
                .limit(5).toList();

        return new Indicadores(estoque.size(), estoque.stream().filter(Anuncio::estaNaLoja).count(), valorEstoque,
                custoEstoque, vendidosNoMes.size(), faturamentoMes, lucroMes, vendidos.size(), lucroTotal, ticketMedio,
                diasMedios, propostas.countByStatus(StatusProposta.NOVA), ultimosMeses, ultimasVendas);
    }

    @Transactional(readOnly = true)
    public Planilha planilhaEstoque() {
        Map<Long, List<Despesa>> porAnuncio = despesasPorAnuncio();
        List<Linha> linhas = new ArrayList<>();
        for (Anuncio a : anuncios.findAllByOrderByAtualizadoEmDesc()) {
            if (a.estaVendido()) {
                continue;
            }
            Rentabilidade r = rentabilidade(a, porAnuncio);
            Map<String, String> v = new LinkedHashMap<>();
            v.put("Placa", a.getVeiculo().getPlacaFormatada());
            v.put("Veículo", a.getTitulo());
            v.put("Ano", a.getAnos());
            v.put("KM", String.valueOf(a.getQuilometragem()));
            v.put("Combustível", a.getCombustivel());
            v.put("Câmbio", a.getCambio());
            v.put("Situação", a.getStatus().getDescricao());
            v.put("Data de compra", a.getDataCompra() == null ? "" : a.getDataCompra().format(DATA));
            v.put("Dias em estoque", a.getDataCompra() == null ? "" : String.valueOf(ChronoUnit.DAYS.between(a.getDataCompra(), LocalDate.now())));
            v.put("Preço anunciado", numero(a.getPreco()));
            v.put("Valor de compra", numero(r.valorCompra()));
            v.put("Despesas", numero(r.totalDespesas()));
            v.put("Custo total", numero(r.custoTotal()));
            v.put("Lucro previsto", numero(r.lucro()));
            v.put("Margem prevista (%)", numero(r.margemPercentual()));
            linhas.add(new Linha(v));
        }
        return Planilha.deNomes("Estoque", List.of("Placa", "Veículo", "Ano", "KM", "Combustível", "Câmbio", "Situação",
                "Data de compra", "Dias em estoque", "Preço anunciado", "Valor de compra", "Despesas", "Custo total",
                "Lucro previsto", "Margem prevista (%)"), linhas);
    }

    /** Mesmas colunas aceitas pela importação de despesas, para ida e volta sem ajustes. */
    @Transactional(readOnly = true)
    public Planilha planilhaDespesas() {
        List<Linha> linhas = new ArrayList<>();
        for (Despesa d : despesas.findAllByOrderByDataDescIdDesc()) {
            Map<String, String> v = new LinkedHashMap<>();
            v.put("Data", d.getData().format(DATA));
            v.put("Placa", d.getAnuncio().getVeiculo().getPlacaFormatada());
            v.put("Veículo", d.getAnuncio().getTitulo());
            v.put("Categoria", d.getCategoria().getDescricao());
            v.put("Descrição", d.getDescricao() == null ? "" : d.getDescricao());
            v.put("Valor", numero(d.getValor()));
            v.put("Origem", d.getOrigem());
            linhas.add(new Linha(v));
        }
        return Planilha.deNomes("Despesas", List.of("Data", "Placa", "Veículo", "Categoria", "Descrição", "Valor", "Origem"), linhas);
    }

    @Transactional(readOnly = true)
    public Planilha planilhaVendas() {
        Map<Long, List<Despesa>> porAnuncio = despesasPorAnuncio();
        List<Linha> linhas = new ArrayList<>();
        anuncios.findAll().stream().filter(Anuncio::estaVendido)
                .sorted(Comparator.comparing((Anuncio a) -> a.getVenda().getData()).reversed())
                .forEach(a -> {
                    Rentabilidade r = rentabilidade(a, porAnuncio);
                    Map<String, String> v = new LinkedHashMap<>();
                    v.put("Data da venda", a.getVenda().getData().format(DATA));
                    v.put("Placa", a.getVeiculo().getPlacaFormatada());
                    v.put("Veículo", a.getTitulo());
                    v.put("Comprador", a.getVenda().getCompradorNome());
                    v.put("CPF/CNPJ", a.getVenda().getDocumentoFormatado());
                    v.put("Forma de pagamento", a.getVenda().getFormaPagamento().getDescricao());
                    v.put("Valor da venda", numero(a.getVenda().getValor()));
                    v.put("Custo total", numero(r.custoTotal()));
                    v.put("Lucro", numero(r.lucro()));
                    v.put("Margem (%)", numero(r.margemPercentual()));
                    linhas.add(new Linha(v));
                });
        return Planilha.deNomes("Vendas", List.of("Data da venda", "Placa", "Veículo", "Comprador", "CPF/CNPJ",
                "Forma de pagamento", "Valor da venda", "Custo total", "Lucro", "Margem (%)"), linhas);
    }

    private Map<Long, List<Despesa>> despesasPorAnuncio() {
        return despesas.findAllByOrderByDataDescIdDesc().stream()
                .collect(Collectors.groupingBy(d -> d.getAnuncio().getId()));
    }

    private static Rentabilidade rentabilidade(Anuncio a, Map<Long, List<Despesa>> porAnuncio) {
        return Rentabilidade.calcular(a, porAnuncio.getOrDefault(a.getId(), List.of()));
    }

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public record Indicadores(long emEstoque, long naLoja, BigDecimal valorEstoque, BigDecimal custoEstoque,
                              long vendasMes, BigDecimal faturamentoMes, BigDecimal lucroMes, long vendasTotal,
                              BigDecimal lucroTotal, BigDecimal ticketMedio, long diasMediosEstoque, long propostasNovas,
                              List<VendaMes> ultimosMeses, List<Anuncio> ultimasVendas) {

        public BigDecimal maiorFaturamentoMensal() {
            return ultimosMeses.stream().map(VendaMes::faturamento).max(BigDecimal::compareTo)
                    .filter(v -> v.signum() > 0).orElse(BigDecimal.ONE);
        }

        public int percentualBarra(VendaMes mes) {
            return mes.faturamento().multiply(BigDecimal.valueOf(100))
                    .divide(maiorFaturamentoMensal(), 0, RoundingMode.HALF_UP).intValue();
        }
    }

    public record VendaMes(String rotulo, int quantidade, BigDecimal faturamento) {
    }
}
