package br.com.dataflowauto.comum.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Dados de contato da loja exibidos no site, configuráveis em application.properties. */
@Component("loja")
public class DadosLoja {

    private final String nome;
    private final String telefone;
    private final String whatsapp;
    private final String endereco;
    private final BigDecimal taxaFinanciamento;

    public DadosLoja(@Value("${dataflow.loja.nome:Imperium Motors}") String nome,
                     @Value("${dataflow.loja.telefone:}") String telefone,
                     @Value("${dataflow.loja.whatsapp:}") String whatsapp,
                     @Value("${dataflow.loja.endereco:}") String endereco,
                     @Value("${dataflow.loja.taxa-financiamento:1.79}") BigDecimal taxaFinanciamento) {
        this.nome = nome;
        this.telefone = telefone;
        this.whatsapp = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
        this.endereco = endereco;
        this.taxaFinanciamento = taxaFinanciamento;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public BigDecimal getTaxaFinanciamento() {
        return taxaFinanciamento;
    }

    public String getTaxaFormatada() {
        return taxaFinanciamento.stripTrailingZeros().toPlainString().replace('.', ',') + "% a.m.";
    }

    /** Parcela de referência exibida nos cartões: 30% de entrada e 48 vezes, pela Tabela Price. */
    public BigDecimal parcelaDesde(BigDecimal preco) {
        if (preco == null || preco.signum() <= 0) {
            return null;
        }
        BigDecimal entrada = preco.multiply(new BigDecimal("0.30"));
        return br.com.dataflowauto.vendas.dominio.SimulacaoFinanciamento
                .calcular(preco, entrada, 48, taxaFinanciamento).valorParcela();
    }

    /** WhatsApp no formato de leitura: "(19) 99999-0000". */
    public String getWhatsappFormatado() {
        String numero = whatsapp.startsWith("55") && whatsapp.length() > 11 ? whatsapp.substring(2) : whatsapp;
        if (numero.length() == 11) {
            return "(" + numero.substring(0, 2) + ") " + numero.substring(2, 7) + "-" + numero.substring(7);
        }
        if (numero.length() == 10) {
            return "(" + numero.substring(0, 2) + ") " + numero.substring(2, 6) + "-" + numero.substring(6);
        }
        return numero;
    }

    /** Cidade do endereço ("Av. das Amoreiras, 1000 - Campinas/SP" → "Campinas"), ou vazio. */
    public String getCidade() {
        if (endereco == null || !endereco.contains(" - ")) {
            return "";
        }
        String fim = endereco.substring(endereco.lastIndexOf(" - ") + 3);
        return fim.contains("/") ? fim.substring(0, fim.indexOf('/')).trim() : fim.trim();
    }

    public boolean temWhatsapp() {
        return !whatsapp.isEmpty();
    }

    /** Link do WhatsApp com uma mensagem já escrita, ou vazio se não houver número configurado. */
    public String whatsapp(String mensagem) {
        if (whatsapp.isEmpty()) {
            return "";
        }
        return "https://wa.me/" + whatsapp + "?text=" + java.net.URLEncoder.encode(mensagem, java.nio.charset.StandardCharsets.UTF_8);
    }
}
