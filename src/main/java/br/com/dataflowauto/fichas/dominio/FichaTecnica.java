package br.com.dataflowauto.fichas.dominio;

import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ficha técnica de um veículo. Nasce com todos os campos do catálogo como AUSENTES;
 * as fontes de dados e o usuário vão preenchendo, e a ficha só é concluída quando
 * os campos obrigatórios estão confirmados e não há nada pendente de confirmação.
 */
@Entity
@Table(name = "ficha_tecnica")
public class FichaTecnica {

    private static final String MARCA_INDISPONIVEL = " (indisponível, não consultada)";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "veiculo_id")
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arquivo_origem_id")
    private ArquivoImportado arquivoOrigem;

    @OneToMany(mappedBy = "ficha", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<CampoFicha> campos = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusFicha status;

    @Column(name = "fontes_consultadas", length = 1000)
    private String fontesConsultadas;

    @Version
    private long versao;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm;

    protected FichaTecnica() {
    }

    public FichaTecnica(Veiculo veiculo) {
        this.veiculo = veiculo;
        for (CampoTecnico campo : CampoTecnico.values()) {
            campos.add(new CampoFicha(this, campo));
        }
        this.status = StatusFicha.EM_REVISAO;
        this.criadaEm = LocalDateTime.now();
        this.atualizadaEm = criadaEm;
    }

    /**
     * Registra um valor vindo de uma fonte. Não sobrescreve um campo já preenchido:
     * a primeira fonte consultada tem prioridade, e o usuário decide na revisão.
     *
     * @return true se o valor foi usado
     */
    public boolean registrarDado(CampoTecnico campo, String valor, OrigemDado origem, String detalheFonte) {
        CampoFicha alvo = campo(campo);
        if (alvo.possuiValor() || valor == null || valor.isBlank()) {
            return false;
        }
        alvo.preencher(valor, origem, detalheFonte);
        tocar();
        return true;
    }

    /** Edição feita pelo usuário na revisão. Um valor novo passa a ter origem USUARIO e fica confirmado. */
    public void editar(CampoTecnico campo, String valor) {
        CampoFicha alvo = campo(campo);
        String novo = valor == null ? "" : valor.trim();
        if (novo.isEmpty()) {
            alvo.limpar();
        } else if (!novo.equals(alvo.getValor())) {
            alvo.preencher(novo, OrigemDado.USUARIO, null);
        }
        tocar();
    }

    public void confirmar(CampoTecnico campo) {
        campo(campo).confirmar();
        tocar();
    }

    public void concluir() {
        List<CampoFicha> pendentes = camposPendentes();
        if (!pendentes.isEmpty()) {
            throw new RegraNegocioException("Ainda há campos pendentes: "
                    + String.join(", ", pendentes.stream().map(c -> c.getCampo().getRotulo()).toList()) + ".");
        }
        this.status = StatusFicha.CONCLUIDA;
        veiculo.atualizarIdentificacao(this);
        tocar();
    }

    /** Campos que impedem a conclusão: obrigatórios não confirmados e qualquer valor ainda a confirmar. */
    public List<CampoFicha> camposPendentes() {
        return campos.stream().filter(CampoFicha::estaPendente).toList();
    }

    public boolean estaCompleta() {
        return camposPendentes().isEmpty();
    }

    public Optional<String> valorConfirmado(CampoTecnico campo) {
        CampoFicha c = campo(campo);
        return c.estaConfirmado() ? Optional.of(c.getValor()) : Optional.empty();
    }

    public CampoFicha campo(CampoTecnico campo) {
        return campos.stream().filter(c -> c.getCampo() == campo).findFirst()
                .orElseThrow(() -> new IllegalStateException("Campo ausente na ficha: " + campo));
    }

    public Map<GrupoCampo, List<CampoFicha>> camposPorGrupo() {
        Map<GrupoCampo, List<CampoFicha>> mapa = new EnumMap<>(GrupoCampo.class);
        for (CampoFicha c : campos) {
            mapa.computeIfAbsent(c.getCampo().getGrupo(), g -> new ArrayList<>()).add(c);
        }
        return new LinkedHashMap<>(mapa);
    }

    /** 7 lugares ou mais pelo valor atual (confirmado ou não), para a revisão mostrar o porta-malas com 7 lugares. */
    public boolean temSeteLugaresOuMais() {
        String valor = campo(CampoTecnico.LUGARES).getValor();
        String lugares = valor == null ? "" : valor.replaceAll("\\D", "");
        return !lugares.isEmpty() && lugares.length() <= 2 && Integer.parseInt(lugares) >= 7;
    }

    public long contar(StatusVerificacao statusVerificacao) {
        return campos.stream().filter(c -> c.getStatus() == statusVerificacao).count();
    }

    public void registrarFontesConsultadas(List<String> fontes) {
        this.fontesConsultadas = fontes.isEmpty() ? "Nenhuma fonte automática" : String.join("; ", fontes);
    }

    /** Como uma fonte que não respondeu aparece na lista de fontes consultadas. */
    public static String fonteIndisponivel(String fonte) {
        return fonte + MARCA_INDISPONIVEL;
    }

    /**
     * Alguma fonte não respondeu na última consulta: o que falta na ficha pode existir
     * (multas, restrições) e só não foi verificado.
     */
    public boolean consultaIncompleta() {
        return fontesConsultadas != null && fontesConsultadas.contains(MARCA_INDISPONIVEL);
    }

    public void vincularArquivo(ArquivoImportado arquivo) {
        this.arquivoOrigem = arquivo;
    }

    private void tocar() {
        this.atualizadaEm = LocalDateTime.now();
        if (status == StatusFicha.CONCLUIDA && !estaCompleta()) {
            status = StatusFicha.EM_REVISAO;
        }
    }

    public Long getId() {
        return id;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public ArquivoImportado getArquivoOrigem() {
        return arquivoOrigem;
    }

    public List<CampoFicha> getCampos() {
        return Collections.unmodifiableList(campos);
    }

    public StatusFicha getStatus() {
        return status;
    }

    public String getFontesConsultadas() {
        return fontesConsultadas;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public LocalDateTime getAtualizadaEm() {
        return atualizadaEm;
    }
}
