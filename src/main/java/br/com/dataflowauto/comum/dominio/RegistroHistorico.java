package br.com.dataflowauto.comum.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/** Uma operação realizada no sistema, para consulta posterior. */
@Entity
@Table(name = "registro_historico")
public class RegistroHistorico {

    private static final int TAMANHO_MAXIMO_DETALHES = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoOperacao operacao;

    @Column(length = TAMANHO_MAXIMO_DETALHES)
    private String detalhes;

    @Column(name = "realizado_em", nullable = false)
    private LocalDateTime realizadoEm;

    protected RegistroHistorico() {
    }

    public RegistroHistorico(TipoOperacao operacao, String detalhes) {
        this.operacao = Objects.requireNonNull(operacao);
        this.detalhes = detalhes != null && detalhes.length() > TAMANHO_MAXIMO_DETALHES
                ? detalhes.substring(0, TAMANHO_MAXIMO_DETALHES)
                : detalhes;
        this.realizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public LocalDateTime getRealizadoEm() {
        return realizadoEm;
    }
}
