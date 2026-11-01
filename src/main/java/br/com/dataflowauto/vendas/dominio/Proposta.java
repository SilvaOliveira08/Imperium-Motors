package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contato de um interessado feito pela página do carro: dúvida, test drive, proposta ou financiamento. */
@Entity
@Table(name = "proposta")
public class Proposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long versao;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "anuncio_id")
    private Anuncio anuncio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProposta tipo;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Column(length = 150)
    private String email;

    @Column(length = 2000)
    private String mensagem;

    @Column(name = "valor_oferecido")
    private BigDecimal valorOferecido;

    @Column(name = "data_preferida")
    private LocalDate dataPreferida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProposta status;

    /** Canal de divulgação de onde o visitante chegou (link com "?origem="); vazio se veio direto. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CanalDivulgacao origem;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm;

    protected Proposta() {
    }

    public Proposta(Anuncio anuncio, NovaProposta dados) {
        if (!anuncio.estaNaLoja()) {
            throw new RegraNegocioException("Este carro não está mais disponível.");
        }
        TipoProposta tipoInformado = dados.tipo() == null ? TipoProposta.DUVIDA : dados.tipo();
        String nomeLimpo = texto(dados.nome());
        if (nomeLimpo == null || nomeLimpo.length() < 2) {
            throw new RegraNegocioException("Informe seu nome.");
        }
        String telefoneLimpo = dados.telefone() == null ? "" : dados.telefone().replaceAll("\\D", "");
        String emailLimpo = texto(dados.email());
        if (telefoneLimpo.isEmpty() && emailLimpo == null) {
            throw new RegraNegocioException("Informe um telefone ou e-mail para a loja responder.");
        }
        if (!telefoneLimpo.isEmpty() && (telefoneLimpo.length() < 10 || telefoneLimpo.length() > 11)) {
            throw new RegraNegocioException("Telefone inválido. Use DDD + número, por exemplo (19) 99999-0000.");
        }
        if (emailLimpo != null && !emailLimpo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new RegraNegocioException("E-mail inválido.");
        }
        if (tipoInformado == TipoProposta.PROPOSTA) {
            Dinheiro.positivo(dados.valorOferecido(), "o valor da sua proposta");
        }
        if (tipoInformado == TipoProposta.TEST_DRIVE) {
            if (dados.dataPreferida() == null) {
                throw new RegraNegocioException("Escolha a data preferida para o test drive.");
            }
            if (dados.dataPreferida().isBefore(LocalDate.now())) {
                throw new RegraNegocioException("A data do test drive não pode estar no passado.");
            }
        }
        String mensagemLimpa = texto(dados.mensagem());
        if (mensagemLimpa != null && mensagemLimpa.length() > 2000) {
            throw new RegraNegocioException("A mensagem pode ter no máximo 2000 caracteres.");
        }
        this.anuncio = anuncio;
        this.tipo = tipoInformado;
        this.nome = nomeLimpo.length() > 150 ? nomeLimpo.substring(0, 150) : nomeLimpo;
        this.telefone = telefoneLimpo.isEmpty() ? null : telefoneLimpo;
        this.email = emailLimpo;
        this.mensagem = mensagemLimpa;
        this.valorOferecido = tipoInformado == TipoProposta.PROPOSTA ? dados.valorOferecido() : null;
        this.dataPreferida = tipoInformado == TipoProposta.TEST_DRIVE ? dados.dataPreferida() : null;
        this.status = StatusProposta.NOVA;
        this.criadaEm = LocalDateTime.now();
        this.atualizadaEm = criadaEm;
    }

    public void alterarStatus(StatusProposta novo) {
        if (novo == null) {
            throw new RegraNegocioException("Situação inválida.");
        }
        this.status = novo;
        this.atualizadaEm = LocalDateTime.now();
    }

    public void registrarOrigem(CanalDivulgacao canal) {
        this.origem = canal;
    }

    public CanalDivulgacao getOrigem() {
        return origem;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public Long getId() {
        return id;
    }

    public Anuncio getAnuncio() {
        return anuncio;
    }

    public TipoProposta getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getTelefoneFormatado() {
        if (telefone == null) {
            return "";
        }
        return telefone.length() == 11
                ? telefone.replaceFirst("(\\d{2})(\\d{5})(\\d{4})", "($1) $2-$3")
                : telefone.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "($1) $2-$3");
    }

    /** Link para conversar pelo WhatsApp (celulares brasileiros têm 11 dígitos). */
    public String getLinkWhatsapp() {
        return telefone != null && telefone.length() == 11 ? "https://wa.me/55" + telefone : null;
    }

    public String getEmail() {
        return email;
    }

    public String getMensagem() {
        return mensagem;
    }

    public BigDecimal getValorOferecido() {
        return valorOferecido;
    }

    public LocalDate getDataPreferida() {
        return dataPreferida;
    }

    public StatusProposta getStatus() {
        return status;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public LocalDateTime getAtualizadaEm() {
        return atualizadaEm;
    }
}
