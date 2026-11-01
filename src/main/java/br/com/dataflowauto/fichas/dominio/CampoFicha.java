package br.com.dataflowauto.fichas.dominio;

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

/**
 * Um campo da ficha com seu valor, a origem do valor e o status de verificação.
 * O estado só muda pelos métodos de negócio, garantindo que um valor vindo de documento
 * ou fonte externa nunca apareça como confirmado sem passar pelo usuário.
 */
@Entity
@Table(name = "campo_ficha")
public class CampoFicha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_id")
    private FichaTecnica ficha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampoTecnico campo;

    @Column(length = 2000)
    private String valor;

    @Enumerated(EnumType.STRING)
    private OrigemDado origem;

    @Column(name = "fonte_detalhe")
    private String fonteDetalhe;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_verificacao", nullable = false)
    private StatusVerificacao status;

    protected CampoFicha() {
    }

    CampoFicha(FichaTecnica ficha, CampoTecnico campo) {
        this.ficha = ficha;
        this.campo = campo;
        this.status = StatusVerificacao.AUSENTE;
    }

    /** Registra um valor encontrado. Dados do usuário já entram confirmados; os demais ficam a confirmar. */
    void preencher(String novoValor, OrigemDado novaOrigem, String detalheFonte) {
        if (novoValor == null || novoValor.isBlank()) {
            limpar();
            return;
        }
        this.valor = novoValor.trim();
        this.origem = novaOrigem;
        this.fonteDetalhe = detalheFonte;
        this.status = novaOrigem == OrigemDado.USUARIO ? StatusVerificacao.CONFIRMADO : StatusVerificacao.A_CONFIRMAR;
    }

    void confirmar() {
        if (!possuiValor()) {
            throw new RegraNegocioException("Não é possível confirmar \"" + campo.getRotulo() + "\" sem valor.");
        }
        this.status = StatusVerificacao.CONFIRMADO;
    }

    void limpar() {
        this.valor = null;
        this.origem = null;
        this.fonteDetalhe = null;
        this.status = StatusVerificacao.AUSENTE;
    }

    public boolean possuiValor() {
        return valor != null && !valor.isBlank();
    }

    public boolean estaConfirmado() {
        return status == StatusVerificacao.CONFIRMADO;
    }

    public boolean estaPendente() {
        return status == StatusVerificacao.A_CONFIRMAR || (campo.isObrigatorio() && status != StatusVerificacao.CONFIRMADO);
    }

    public Long getId() {
        return id;
    }

    public CampoTecnico getCampo() {
        return campo;
    }

    public String getValor() {
        return valor;
    }

    public OrigemDado getOrigem() {
        return origem;
    }

    public String getFonteDetalhe() {
        return fonteDetalhe;
    }

    public StatusVerificacao getStatus() {
        return status;
    }
}
