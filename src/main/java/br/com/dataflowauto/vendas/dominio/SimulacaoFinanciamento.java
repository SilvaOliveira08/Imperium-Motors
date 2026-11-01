package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Simulação pela Tabela Price: parcela = PV · i / (1 − (1 + i)^−n).
 * É só uma estimativa para o cliente; a taxa real depende da análise de crédito.
 */
public record SimulacaoFinanciamento(BigDecimal valorVeiculo, BigDecimal entrada, int parcelas,
                                     BigDecimal taxaMensalPercentual, BigDecimal valorFinanciado,
                                     BigDecimal valorParcela, BigDecimal totalPago, BigDecimal totalJuros) {

    public static final int MINIMO_PARCELAS = 6;
    public static final int MAXIMO_PARCELAS = 72;

    public static SimulacaoFinanciamento calcular(BigDecimal valorVeiculo, BigDecimal entrada, int parcelas,
                                                  BigDecimal taxaMensalPercentual) {
        BigDecimal valor = Dinheiro.positivo(valorVeiculo, "o valor do veículo");
        BigDecimal ent = Dinheiro.ouZero(entrada).setScale(2, RoundingMode.HALF_UP);
        if (ent.signum() < 0) {
            throw new RegraNegocioException("A entrada não pode ser negativa.");
        }
        if (ent.compareTo(valor) >= 0) {
            throw new RegraNegocioException("A entrada cobre o valor do carro: não há o que financiar.");
        }
        if (parcelas < MINIMO_PARCELAS || parcelas > MAXIMO_PARCELAS) {
            throw new RegraNegocioException("Escolha entre " + MINIMO_PARCELAS + " e " + MAXIMO_PARCELAS + " parcelas.");
        }
        if (taxaMensalPercentual == null || taxaMensalPercentual.signum() < 0) {
            throw new RegraNegocioException("Taxa de juros inválida.");
        }
        BigDecimal financiado = valor.subtract(ent);
        BigDecimal parcela;
        if (taxaMensalPercentual.signum() == 0) {
            parcela = financiado.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        } else {
            MathContext mc = MathContext.DECIMAL64;
            BigDecimal i = taxaMensalPercentual.divide(BigDecimal.valueOf(100), mc);
            BigDecimal fator = BigDecimal.ONE.add(i).pow(-parcelas, mc);
            parcela = financiado.multiply(i, mc).divide(BigDecimal.ONE.subtract(fator), mc).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = parcela.multiply(BigDecimal.valueOf(parcelas));
        return new SimulacaoFinanciamento(valor, ent, parcelas, taxaMensalPercentual, financiado, parcela,
                total.add(ent), total.subtract(financiado));
    }
}
