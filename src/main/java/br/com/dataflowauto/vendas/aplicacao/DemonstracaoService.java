package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.CategoriaDespesa;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.NovaProposta;
import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import br.com.dataflowauto.vendas.dominio.TipoProposta;
import br.com.dataflowauto.vendas.dominio.Venda;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import br.com.dataflowauto.fichas.dominio.Placa;
import br.com.dataflowauto.fichas.infra.VeiculoRepository;
import br.com.dataflowauto.vendas.aplicacao.CatalogoDemonstracao.CarroDemo;
import br.com.dataflowauto.vendas.aplicacao.CatalogoDemonstracao.Contato;
import br.com.dataflowauto.vendas.aplicacao.CatalogoDemonstracao.FichaPendente;
import br.com.dataflowauto.vendas.aplicacao.CatalogoDemonstracao.Gasto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta uma loja de exemplo usando o fluxo real: cria as fichas pelas placas da base de
 * demonstração, confirma e conclui, anuncia, lança despesas, registra um interessado e uma venda.
 * Depois monta o estoque fictício de {@link CatalogoDemonstracao}.
 * Só cria o que ainda não existe, então pode ser chamado mais de uma vez.
 */
@Service
public class DemonstracaoService {

    private final FichaService fichas;
    private final AnuncioService anuncios;
    private final DespesaService despesas;
    private final PropostaService propostas;
    private final AnuncioRepository repositorio;
    private final VeiculoRepository veiculos;

    public DemonstracaoService(FichaService fichas, AnuncioService anuncios, DespesaService despesas,
                               PropostaService propostas, AnuncioRepository repositorio, VeiculoRepository veiculos) {
        this.fichas = fichas;
        this.anuncios = anuncios;
        this.despesas = despesas;
        this.propostas = propostas;
        this.repositorio = repositorio;
        this.veiculos = veiculos;
    }

    @Transactional
    public int carregar() {
        LocalDate hoje = LocalDate.now();
        int criados = 0;

        Anuncio polo = anunciar("ABC1D23", "Branco Cristal", new DadosAnuncio(null, new BigDecimal("109900"), 18500,
                new BigDecimal("92000"), hoje.minusDays(25),
                "Único dono, revisões na concessionária, manual e chave reserva. Pneus novos.", true));
        if (polo != null) {
            criados++;
            despesas.adicionar(polo.getId(), hoje.minusDays(20), CategoriaDespesa.ESTETICA, "Polimento e higienização", new BigDecimal("450"));
            despesas.adicionar(polo.getId(), hoje.minusDays(18), CategoriaDespesa.PNEUS, "Jogo de pneus 205/55 R16", new BigDecimal("2380"));
            despesas.adicionar(polo.getId(), hoje.minusDays(15), CategoriaDespesa.DOCUMENTACAO, "Transferência e vistoria", new BigDecimal("620"));
            propostas.registrar(polo.getId(), new NovaProposta(TipoProposta.TEST_DRIVE, "Mariana Costa", "19998887766",
                    "mariana@example.com", "Posso ir no sábado de manhã?", null, hoje.plusDays(3)));
        }

        Anuncio strada = anunciar("BRA2E19", "Cinza Silverstone", new DadosAnuncio(null, new BigDecimal("124500"), 32000,
                new BigDecimal("108000"), hoje.minusDays(12),
                "Cabine dupla, capota marítima, engate e multimídia. Laudo cautelar aprovado.", false));
        if (strada != null) {
            criados++;
            despesas.adicionar(strada.getId(), hoje.minusDays(10), CategoriaDespesa.MECANICA, "Revisão dos 30 mil km", new BigDecimal("1150"));
            despesas.adicionar(strada.getId(), hoje.minusDays(9), CategoriaDespesa.ANUNCIOS, "Fotos profissionais e anúncio", new BigDecimal("280"));
            propostas.registrar(strada.getId(), new NovaProposta(TipoProposta.PROPOSTA, "Carlos Pereira", "19991234567",
                    null, "Tenho um Gol 2015 para dar na troca.", new BigDecimal("118000"), null));
        }

        Anuncio onix = anunciar("XYZ9876", "Vermelho Carmim", new DadosAnuncio(null, new BigDecimal("64900"), 61000,
                new BigDecimal("52000"), hoje.minusDays(40), "Econômico, ótimo para o dia a dia.", false));
        if (onix != null) {
            criados++;
            despesas.adicionar(onix.getId(), hoje.minusDays(35), CategoriaDespesa.FUNILARIA_PINTURA, "Retoque no para-choque", new BigDecimal("900"));
            despesas.adicionar(onix.getId(), hoje.minusDays(33), CategoriaDespesa.IPVA_LICENCIAMENTO, "Licenciamento do ano", new BigDecimal("160"));
            anuncios.registrarVenda(onix.getId(), new Venda("João da Silva", "12345678909", new BigDecimal("63000"),
                    hoje.minusDays(5), FormaPagamento.FINANCIAMENTO), null);
        }

        for (CarroDemo carro : CatalogoDemonstracao.carros()) {
            if (montar(carro, hoje)) {
                criados++;
            }
        }
        for (FichaPendente pendente : CatalogoDemonstracao.fichasPendentes()) {
            if (veiculos.findByPlaca(pendente.placa()).isEmpty()) {
                fichas.criar(new NovaFichaComando(pendente.placa(), null, pendente.valores()));
            }
        }
        completarProcedencia();
        return criados;
    }

    /**
     * Fichas de demonstração criadas antes de existir a procedência ganham multas, débitos,
     * restrições e proprietários pela mesma "consulta novamente" do painel. Só mexe nas placas
     * fictícias, e só confirma de novo as que já estavam concluídas.
     */
    private void completarProcedencia() {
        Set<String> placasDemo = CatalogoDemonstracao.placas();
        for (FichaTecnica ficha : fichas.listar()) {
            if (!placasDemo.contains(ficha.getVeiculo().getPlaca().valor())
                    || ficha.campo(CampoTecnico.QUANTIDADE_DONOS).possuiValor()) {
                continue;
            }
            boolean estavaConcluida = ficha.getStatus() == StatusFicha.CONCLUIDA;
            if (fichas.reconsultar(ficha.getId()) > 0 && estavaConcluida) {
                fichas.revisar(ficha.getId(), Map.of(), EnumSet.allOf(CampoTecnico.class), true);
            }
        }
    }

    /** Leva um carro do catálogo pelo mesmo caminho da equipe: ficha, anúncio, custos, contatos e venda. */
    private boolean montar(CarroDemo carro, LocalDate hoje) {
        Map<CampoTecnico, String> ficha = new EnumMap<>(carro.ficha());
        ficha.put(CampoTecnico.COR, carro.cor());
        Anuncio anuncio = anunciar(carro.placa(), ficha, new DadosAnuncio(null, BigDecimal.valueOf(carro.preco()),
                carro.quilometragem(), BigDecimal.valueOf(carro.valorCompra()), hoje.minusDays(carro.diasDesdeCompra()),
                carro.descricao(), carro.destaque()));
        if (anuncio == null) {
            return false;
        }
        for (Gasto g : carro.gastos()) {
            despesas.adicionar(anuncio.getId(), hoje.minusDays(g.diasAtras()), g.categoria(), g.descricao(), BigDecimal.valueOf(g.valor()));
        }
        List<Proposta> registradas = new ArrayList<>();
        for (Contato c : carro.contatos()) {
            Proposta p = propostas.registrar(anuncio.getId(), new NovaProposta(c.tipo(), c.nome(), c.telefone(), c.email(),
                    c.mensagem(), c.valorOferecido() == null ? null : BigDecimal.valueOf(c.valorOferecido()),
                    c.diasTestDrive() == null ? null : hoje.plusDays(c.diasTestDrive())));
            if (c.status() != StatusProposta.NOVA) {
                propostas.alterarStatus(p.getId(), c.status());
            }
            registradas.add(p);
        }
        if (carro.venda() != null) {
            var v = carro.venda();
            Long origem = v.peloPrimeiroContato() && !registradas.isEmpty() ? registradas.get(0).getId() : null;
            anuncios.registrarVenda(anuncio.getId(), new Venda(v.comprador(), v.documento(), BigDecimal.valueOf(v.valor()),
                    hoje.minusDays(v.diasAtras()), v.forma()), origem);
        } else if (carro.situacao() != StatusAnuncio.DISPONIVEL) {
            anuncios.alterarStatus(anuncio.getId(), carro.situacao());
        }
        return true;
    }

    /** Cria ficha + anúncio para a placa, se ela ainda não tiver anúncio. */
    private Anuncio anunciar(String placa, String cor, DadosAnuncio dados) {
        return anunciar(placa, Map.of(CampoTecnico.COR, cor), dados);
    }

    private Anuncio anunciar(String placa, Map<CampoTecnico, String> valores, DadosAnuncio dados) {
        if (!repositorio.findByFichaVeiculoPlacaOrderByCriadoEmDesc(new Placa(placa).valor()).isEmpty()) {
            return null;
        }
        FichaTecnica ficha = fichas.criar(new NovaFichaComando(placa, null, valores));
        EnumSet<CampoTecnico> todos = EnumSet.allOf(CampoTecnico.class);
        fichas.revisar(ficha.getId(), Map.of(), todos, true);
        return anuncios.criar(ficha.getId(), dados, List.of());
    }
}
