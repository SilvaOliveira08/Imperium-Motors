package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.DadosAnuncio;
import br.com.dataflowauto.vendas.dominio.Dinheiro;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Campos do formulário de anúncio, em texto, como o usuário digita ("R$ 89.900,00", "45.000"). */
public class AnuncioForm {

    private String titulo;
    private String preco;
    private String quilometragem;
    private String valorCompra;
    private String dataCompra;
    private String descricao;
    private boolean destaque;

    public static AnuncioForm de(Anuncio a) {
        AnuncioForm f = new AnuncioForm();
        f.titulo = a.getTitulo();
        f.preco = texto(a.getPreco());
        f.quilometragem = String.valueOf(a.getQuilometragem());
        f.valorCompra = texto(a.getValorCompra());
        f.dataCompra = a.getDataCompra() == null ? "" : a.getDataCompra().toString();
        f.descricao = a.getDescricao();
        f.destaque = a.isDestaque();
        return f;
    }

    public DadosAnuncio paraDados() {
        return new DadosAnuncio(titulo, Dinheiro.interpretar(preco), Conversoes.inteiro(quilometragem, "quilometragem"),
                Dinheiro.interpretar(valorCompra), Conversoes.data(dataCompra, "data de compra"), descricao, destaque);
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getPreco() {
        return preco;
    }

    public void setPreco(String preco) {
        this.preco = preco;
    }

    public String getQuilometragem() {
        return quilometragem;
    }

    public void setQuilometragem(String quilometragem) {
        this.quilometragem = quilometragem;
    }

    public String getValorCompra() {
        return valorCompra;
    }

    public void setValorCompra(String valorCompra) {
        this.valorCompra = valorCompra;
    }

    public String getDataCompra() {
        return dataCompra;
    }

    public void setDataCompra(String dataCompra) {
        this.dataCompra = dataCompra;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isDestaque() {
        return destaque;
    }

    public void setDestaque(boolean destaque) {
        this.destaque = destaque;
    }
}
