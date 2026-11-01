package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.StatusFicha;
import br.com.dataflowauto.fichas.dominio.Veiculo;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Carro anunciado na loja. Os dados técnicos não são digitados de novo: vêm da ficha
 * técnica, e só uma ficha concluída (com os dados confirmados) pode virar anúncio.
 */
@Entity
@Table(name = "anuncio")
public class Anuncio {

    public static final int MAXIMO_FOTOS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "ficha_id")
    private FichaTecnica ficha;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false)
    private BigDecimal preco;

    @Column(name = "valor_compra")
    private BigDecimal valorCompra;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(nullable = false)
    private Integer quilometragem;

    @Column(length = 4000)
    private String descricao;

    @Column(nullable = false)
    private boolean destaque;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAnuncio status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "anuncio_foto", joinColumns = @JoinColumn(name = "anuncio_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "arquivo", length = 80)
    private List<String> fotos = new ArrayList<>();

    @Embedded
    private Venda venda;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long versao;

    /**
     * Veículo deste anúncio enquanto ele não foi vendido (nulo depois da venda). A coluna é única,
     * então o banco recusa um segundo anúncio em andamento para o mesmo carro, mesmo num clique duplo.
     */
    @Column(name = "veiculo_em_andamento_id")
    private Long veiculoEmAndamentoId;

    protected Anuncio() {
    }

    public Anuncio(FichaTecnica ficha, DadosAnuncio dados) {
        if (ficha.getStatus() != StatusFicha.CONCLUIDA) {
            throw new RegraNegocioException("Conclua a ficha técnica da placa " + ficha.getVeiculo().getPlacaFormatada()
                    + " antes de anunciar o carro: a loja só publica dados confirmados.");
        }
        this.ficha = ficha;
        this.status = StatusAnuncio.DISPONIVEL;
        this.veiculoEmAndamentoId = ficha.getVeiculo().getId();
        this.criadoEm = LocalDateTime.now();
        atualizar(dados);
    }

    public void atualizar(DadosAnuncio dados) {
        this.preco = Dinheiro.positivo(dados.preco(), "o preço de venda");
        if (dados.quilometragem() == null || dados.quilometragem() < 0) {
            throw new RegraNegocioException("Informe a quilometragem (0 para carro zero).");
        }
        if (dados.valorCompra() != null && dados.valorCompra().signum() < 0) {
            throw new RegraNegocioException("O valor de compra não pode ser negativo.");
        }
        if (dados.dataCompra() != null && dados.dataCompra().isAfter(LocalDate.now())) {
            throw new RegraNegocioException("A data de compra não pode estar no futuro.");
        }
        this.quilometragem = dados.quilometragem();
        this.valorCompra = dados.valorCompra();
        this.dataCompra = dados.dataCompra();
        this.descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().trim();
        this.destaque = dados.destaque();
        this.titulo = dados.titulo() == null || dados.titulo().isBlank() ? tituloPadrao() : dados.titulo().trim();
        if (titulo.length() > 120) {
            throw new RegraNegocioException("O título pode ter no máximo 120 caracteres.");
        }
        tocar();
    }

    public void alterarStatus(StatusAnuncio novo) {
        if (novo == StatusAnuncio.VENDIDO) {
            throw new RegraNegocioException("Use \"Registrar venda\" para marcar o carro como vendido.");
        }
        if (status == StatusAnuncio.VENDIDO) {
            throw new RegraNegocioException("Este carro já foi vendido. Desfaça a venda antes de mudar a situação.");
        }
        if (novo.isVisivelNaLoja() && ficha.getStatus() != StatusFicha.CONCLUIDA) {
            throw new RegraNegocioException("A ficha técnica voltou para revisão. Conclua a ficha antes de publicar o anúncio.");
        }
        this.status = novo;
        tocar();
    }

    public void registrarVenda(Venda venda) {
        if (status == StatusAnuncio.VENDIDO) {
            throw new RegraNegocioException("Este carro já foi vendido.");
        }
        if (dataCompra != null && venda.getData().isBefore(dataCompra)) {
            throw new RegraNegocioException("A data da venda é anterior à data de compra do carro.");
        }
        this.venda = venda;
        this.status = StatusAnuncio.VENDIDO;
        this.veiculoEmAndamentoId = null;
        this.destaque = false;
        tocar();
    }

    public void desfazerVenda() {
        if (status != StatusAnuncio.VENDIDO) {
            throw new RegraNegocioException("Este carro não está marcado como vendido.");
        }
        this.venda = null;
        this.status = StatusAnuncio.OCULTO;
        this.veiculoEmAndamentoId = ficha.getVeiculo().getId();
        tocar();
    }

    public void adicionarFoto(String arquivo) {
        if (fotos.size() >= MAXIMO_FOTOS) {
            throw new RegraNegocioException("Cada anúncio pode ter no máximo " + MAXIMO_FOTOS + " fotos.");
        }
        fotos.add(arquivo);
        tocar();
    }

    public String removerFoto(int indice) {
        if (indice < 0 || indice >= fotos.size()) {
            throw new RegraNegocioException("Foto não encontrada.");
        }
        String removida = fotos.remove(indice);
        tocar();
        return removida;
    }

    /** Move a foto escolhida para a primeira posição, que é a capa do anúncio. */
    public void definirCapa(int indice) {
        if (indice < 0 || indice >= fotos.size()) {
            throw new RegraNegocioException("Foto não encontrada.");
        }
        fotos.add(0, fotos.remove(indice));
        tocar();
    }

    public boolean estaNaLoja() {
        return status.isVisivelNaLoja() && ficha.getStatus() == StatusFicha.CONCLUIDA;
    }

    public boolean estaVendido() {
        return status == StatusAnuncio.VENDIDO;
    }

    /** Valor confirmado na ficha técnica, ou vazio. Dados não confirmados nunca vão para a loja. */
    public String dado(CampoTecnico campo) {
        return ficha.valorConfirmado(campo).orElse("");
    }

    /**
     * Resumo curto da procedência para o cartão da loja ("1 dono", "sem leilão", "sem sinistro").
     * Só entra o que está confirmado e é bom sinal; o histórico completo fica na ficha do carro.
     */
    public List<String> getProcedencia() {
        List<String> itens = new ArrayList<>();
        String donos = dado(CampoTecnico.QUANTIDADE_DONOS).trim();
        if (donos.matches("\\d+")) {
            itens.add(donos.equals("1") ? "1 dono" : donos + " donos");
        }
        if (nadaConsta(CampoTecnico.LEILAO)) {
            itens.add("sem leilão");
        }
        if (nadaConsta(CampoTecnico.SINISTRO)) {
            itens.add("sem sinistro");
        }
        return itens;
    }

    /** 7 lugares ou mais, pelo número confirmado na ficha ("7", "7 lugares"). */
    public boolean isSeteLugaresOuMais() {
        String lugares = dado(CampoTecnico.LUGARES).replaceAll("\\D", "");
        return !lugares.isEmpty() && lugares.length() <= 2 && Integer.parseInt(lugares) >= 7;
    }

    /**
     * Porta-malas com todos os bancos em uso, para mostrar junto do porta-malas normal.
     * Vazio se o carro tem menos de 7 lugares ou se falta um dos dois valores confirmados.
     */
    public String getPortaMalasSeteLugares() {
        if (!isSeteLugaresOuMais() || dado(CampoTecnico.PORTA_MALAS).isBlank()) {
            return "";
        }
        return dado(CampoTecnico.PORTA_MALAS_7_LUGARES);
    }

    private boolean nadaConsta(CampoTecnico campo) {
        return dado(campo).trim().equalsIgnoreCase("Nada consta");
    }

    public Optional<String> getCapa() {
        return fotos.isEmpty() ? Optional.empty() : Optional.of(fotos.get(0));
    }

    private String tituloPadrao() {
        String texto = String.join(" ", dado(CampoTecnico.MARCA), dado(CampoTecnico.MODELO), dado(CampoTecnico.VERSAO))
                .replaceAll("\\s+", " ").trim();
        return texto.isEmpty() ? ficha.getVeiculo().getDescricao() : texto;
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public FichaTecnica getFicha() {
        return ficha;
    }

    public Veiculo getVeiculo() {
        return ficha.getVeiculo();
    }

    public String getTitulo() {
        return titulo;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public BigDecimal getValorCompra() {
        return valorCompra;
    }

    public LocalDate getDataCompra() {
        return dataCompra;
    }

    public Integer getQuilometragem() {
        return quilometragem;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isDestaque() {
        return destaque;
    }

    public StatusAnuncio getStatus() {
        return status;
    }

    public List<String> getFotos() {
        return Collections.unmodifiableList(fotos);
    }

    public Venda getVenda() {
        return venda;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public String getMarca() {
        return dado(CampoTecnico.MARCA);
    }

    public String getModelo() {
        return dado(CampoTecnico.MODELO);
    }

    public String getAnoModelo() {
        return dado(CampoTecnico.ANO_MODELO);
    }

    public String getAnoFabricacao() {
        return dado(CampoTecnico.ANO_FABRICACAO);
    }

    public String getCombustivel() {
        return dado(CampoTecnico.COMBUSTIVEL);
    }

    public String getCambio() {
        return dado(CampoTecnico.CAMBIO);
    }

    public String getCor() {
        return dado(CampoTecnico.COR);
    }

    /** "2023/2024" quando há os dois anos, senão só o do modelo. */
    public String getAnos() {
        String fab = getAnoFabricacao();
        String mod = getAnoModelo();
        return fab.isEmpty() || fab.equals(mod) ? mod : fab + "/" + mod;
    }

    /** Câmbio simplificado para filtro: "Automático" ou "Manual". */
    public String getTipoCambio() {
        String cambio = CampoTecnico.normalizar(getCambio());
        if (cambio.isEmpty()) {
            return "";
        }
        return cambio.contains("manual") ? "Manual" : "Automático";
    }
}
