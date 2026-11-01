package br.com.dataflowauto.fichas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Veículo identificado pela placa. Marca, modelo e ano são atualizados a partir das fichas revisadas. */
@Entity
@Table(name = "veiculo")
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 7)
    private String placa;

    private String chassi;
    private String marca;
    private String modelo;
    private String versao;

    @Column(name = "ano_modelo")
    private String anoModelo;

    protected Veiculo() {
    }

    public Veiculo(Placa placa) {
        this.placa = placa.valor();
    }

    public Long getId() {
        return id;
    }

    public Placa getPlaca() {
        return new Placa(placa);
    }

    public String getPlacaFormatada() {
        return getPlaca().formatada();
    }

    public String getChassi() {
        return chassi;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public String getVersao() {
        return versao;
    }

    public String getAnoModelo() {
        return anoModelo;
    }

    public String getDescricao() {
        String texto = String.join(" ", naoNulo(marca), naoNulo(modelo), naoNulo(versao), naoNulo(anoModelo)).trim();
        return texto.isEmpty() ? "Veículo sem identificação" : texto.replaceAll("\\s+", " ");
    }

    /** Copia para o veículo apenas os dados de identificação já confirmados na ficha. */
    void atualizarIdentificacao(FichaTecnica ficha) {
        this.marca = ficha.valorConfirmado(CampoTecnico.MARCA).orElse(marca);
        this.modelo = ficha.valorConfirmado(CampoTecnico.MODELO).orElse(modelo);
        this.versao = ficha.valorConfirmado(CampoTecnico.VERSAO).orElse(versao);
        this.anoModelo = ficha.valorConfirmado(CampoTecnico.ANO_MODELO).orElse(anoModelo);
        this.chassi = ficha.valorConfirmado(CampoTecnico.CHASSI).orElse(chassi);
    }

    private static String naoNulo(String s) {
        return s == null ? "" : s;
    }
}
