package br.com.dataflowauto.marketing.dominio;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.vendas.dominio.Anuncio;
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

import java.time.LocalDate;

/**
 * Registro de que um carro foi publicado em um canal (Instagram, OLX...). A equipe marca ao
 * publicar e ao tirar do ar; quando o carro é vendido, publicações ainda ativas viram pendência.
 */
@Entity
@Table(name = "publicacao")
public class Publicacao {

    private static final int TAMANHO_LINK = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "anuncio_id")
    private Anuncio anuncio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalDivulgacao canal;

    @Column(length = TAMANHO_LINK)
    private String link;

    @Column(name = "publicada_em", nullable = false)
    private LocalDate publicadaEm;

    @Column(name = "removida_em")
    private LocalDate removidaEm;

    protected Publicacao() {
    }

    public Publicacao(Anuncio anuncio, CanalDivulgacao canal, String link, LocalDate data) {
        if (canal == null) {
            throw new RegraNegocioException("Escolha o canal.");
        }
        this.anuncio = anuncio;
        this.canal = canal;
        publicar(link, data);
    }

    /** Marca como publicado (de novo, se já tinha saído do ar), com o link do anúncio no canal. */
    public void publicar(String link, LocalDate data) {
        this.link = validarLink(link);
        this.publicadaEm = data == null ? LocalDate.now() : data;
        this.removidaEm = null;
    }

    public void tirarDoAr(LocalDate data) {
        if (!isAtiva()) {
            throw new RegraNegocioException("Este carro já não está no " + canal.getDescricao() + ".");
        }
        this.removidaEm = data == null ? LocalDate.now() : data;
    }

    public boolean isAtiva() {
        return removidaEm == null;
    }

    /** Ativa, mas o carro não está mais à venda na loja (vendido ou fora da loja): precisa sair do ar. */
    public boolean isPendenteRemocao() {
        return isAtiva() && !anuncio.estaNaLoja();
    }

    private static String validarLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String limpo = link.trim();
        if (!limpo.matches("(?i)https?://\\S+")) {
            throw new RegraNegocioException("O link precisa começar com http:// ou https://");
        }
        if (limpo.length() > TAMANHO_LINK) {
            throw new RegraNegocioException("O link pode ter no máximo " + TAMANHO_LINK + " caracteres.");
        }
        return limpo;
    }

    public Long getId() {
        return id;
    }

    public Anuncio getAnuncio() {
        return anuncio;
    }

    public CanalDivulgacao getCanal() {
        return canal;
    }

    public String getLink() {
        return link;
    }

    public LocalDate getPublicadaEm() {
        return publicadaEm;
    }

    public LocalDate getRemovidaEm() {
        return removidaEm;
    }
}
