package br.com.dataflowauto.vendas.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Quanto o carro custou (compra + despesas) e quanto rende. Antes da venda o cálculo usa
 * o preço anunciado (lucro previsto); depois, o valor efetivo da venda (lucro realizado).
 */
public record Rentabilidade(BigDecimal valorCompra, BigDecimal totalDespesas, BigDecimal custoTotal,
                            BigDecimal valorReferencia, BigDecimal lucro, BigDecimal margemPercentual,
                            boolean realizada) {

    public static Rentabilidade calcular(Anuncio anuncio, Collection<Despesa> despesas) {
        BigDecimal compra = Dinheiro.ouZero(anuncio.getValorCompra());
        BigDecimal gastos = despesas.stream().map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal custo = compra.add(gastos);
        boolean realizada = anuncio.estaVendido();
        BigDecimal referencia = realizada ? anuncio.getVenda().getValor() : anuncio.getPreco();
        BigDecimal lucro = referencia.subtract(custo);
        BigDecimal margem = referencia.signum() == 0
                ? BigDecimal.ZERO
                : lucro.multiply(BigDecimal.valueOf(100)).divide(referencia, 1, RoundingMode.HALF_UP);
        return new Rentabilidade(compra, gastos, custo, referencia, lucro, margem, realizada);
    }

    public boolean prejuizo() {
        return lucro.signum() < 0;
    }
}
