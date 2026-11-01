package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados do fechamento do negócio, guardados no próprio anúncio. */
@Embeddable
public class Venda {

    @Column(name = "comprador_nome", length = 150)
    private String compradorNome;

    @Column(name = "comprador_documento", length = 20)
    private String compradorDocumento;

    @Column(name = "valor_venda")
    private BigDecimal valor;

    @Column(name = "data_venda")
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    protected Venda() {
    }

    public Venda(String compradorNome, String compradorDocumento, BigDecimal valor, LocalDate data, FormaPagamento formaPagamento) {
        if (compradorNome == null || compradorNome.isBlank()) {
            throw new RegraNegocioException("Informe o nome do comprador.");
        }
        if (data == null) {
            throw new RegraNegocioException("Informe a data da venda.");
        }
        if (data.isAfter(LocalDate.now())) {
            throw new RegraNegocioException("A data da venda não pode estar no futuro.");
        }
        if (formaPagamento == null) {
            throw new RegraNegocioException("Informe a forma de pagamento.");
        }
        String documento = compradorDocumento == null ? "" : compradorDocumento.replaceAll("\\D", "");
        if (!documento.isEmpty() && documento.length() != 11 && documento.length() != 14) {
            throw new RegraNegocioException("O documento do comprador deve ser um CPF (11 dígitos) ou CNPJ (14 dígitos).");
        }
        this.compradorNome = compradorNome.trim();
        this.compradorDocumento = documento.isEmpty() ? null : documento;
        this.valor = Dinheiro.positivo(valor, "o valor da venda");
        this.data = data;
        this.formaPagamento = formaPagamento;
    }

    public String getCompradorNome() {
        return compradorNome;
    }

    public String getCompradorDocumento() {
        return compradorDocumento;
    }

    public String getDocumentoFormatado() {
        if (compradorDocumento == null) {
            return "";
        }
        if (compradorDocumento.length() == 11) {
            return compradorDocumento.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        return compradorDocumento.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getData() {
        return data;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }
}
