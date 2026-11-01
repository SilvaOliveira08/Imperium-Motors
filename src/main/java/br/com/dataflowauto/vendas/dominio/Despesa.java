package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Gasto com um carro do estoque (preparação, documentação, anúncios...). Guarda de onde
 * veio o lançamento, digitado ou importado de uma planilha, como as fichas fazem com os campos.
 */
@Entity
@Table(name = "despesa")
public class Despesa {

    public static final String ORIGEM_MANUAL = "Lançamento manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anuncio_id")
    private Anuncio anuncio;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaDespesa categoria;

    private String descricao;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private String origem;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    protected Despesa() {
    }

    public Despesa(Anuncio anuncio, LocalDate data, CategoriaDespesa categoria, String descricao, BigDecimal valor, String origem) {
        if (anuncio.estaVendido() && data != null && anuncio.getVenda().getData().isBefore(data)) {
            throw new RegraNegocioException("A despesa é posterior à venda do carro.");
        }
        if (data == null) {
            throw new RegraNegocioException("Informe a data da despesa.");
        }
        if (data.isAfter(LocalDate.now())) {
            throw new RegraNegocioException("A data da despesa não pode estar no futuro.");
        }
        this.anuncio = anuncio;
        this.data = data;
        this.categoria = categoria == null ? CategoriaDespesa.OUTROS : categoria;
        this.descricao = descricao == null || descricao.isBlank() ? null : limitar(descricao.trim(), 255);
        this.valor = Dinheiro.positivo(valor, "o valor da despesa");
        this.origem = origem == null || origem.isBlank() ? ORIGEM_MANUAL : limitar(origem, 255);
        this.criadaEm = LocalDateTime.now();
    }

    /**
     * Identifica o lançamento pelo que ele representa (data, categoria, valor e descrição), sem olhar a origem.
     * Serve para reconhecer a mesma despesa quando uma planilha é importada de novo.
     */
    public String chaveLancamento() {
        return data + "|" + categoria + "|" + valor.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|"
                + (descricao == null ? "" : descricao.trim().toLowerCase(Locale.ROOT));
    }

    private static String limitar(String texto, int tamanho) {
        return texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }

    public Long getId() {
        return id;
    }

    public Anuncio getAnuncio() {
        return anuncio;
    }

    public LocalDate getData() {
        return data;
    }

    public CategoriaDespesa getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getOrigem() {
        return origem;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }
}
