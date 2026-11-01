package br.com.dataflowauto.planilhas.web;

import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.dominio.regras.FiltrarVazios;
import br.com.dataflowauto.planilhas.dominio.regras.PadronizarTexto;
import br.com.dataflowauto.planilhas.dominio.regras.RegraTransformacao;
import br.com.dataflowauto.planilhas.dominio.regras.RemoverDuplicatas;
import br.com.dataflowauto.planilhas.dominio.regras.RenomearColuna;
import br.com.dataflowauto.planilhas.dominio.regras.ReordenarColunas;
import br.com.dataflowauto.planilhas.dominio.regras.SelecionarColunas;
import br.com.dataflowauto.planilhas.dominio.regras.TipoPadronizacao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Formulário de organização. Converte as escolhas da tela em uma lista ordenada de regras. */
public class OrganizacaoForm {

    private List<ColunaForm> colunas = new ArrayList<>();
    private boolean removerDuplicatas;
    private String duplicataPorColuna;
    private String removerVaziosDaColuna;

    public static OrganizacaoForm de(Planilha planilha) {
        OrganizacaoForm form = new OrganizacaoForm();
        List<String> nomes = planilha.getNomesColunas();
        for (int i = 0; i < nomes.size(); i++) {
            ColunaForm c = new ColunaForm();
            c.setNome(nomes.get(i));
            c.setIncluir(true);
            c.setOrdem(i + 1);
            form.colunas.add(c);
        }
        return form;
    }

    /**
     * Ordem de aplicação: padronizar (para que "abc1234" e "ABC-1234" virem a mesma placa),
     * remover vazios, remover duplicatas, selecionar/reordenar e, por último, renomear.
     */
    public List<RegraTransformacao> paraRegras() {
        List<RegraTransformacao> regras = new ArrayList<>();
        for (ColunaForm c : colunas) {
            if (c.getPadronizacao() != null) {
                regras.add(new PadronizarTexto(c.getNome(), c.getPadronizacao()));
            }
        }
        if (temTexto(removerVaziosDaColuna)) {
            regras.add(new FiltrarVazios(removerVaziosDaColuna));
        }
        if (removerDuplicatas) {
            regras.add(temTexto(duplicataPorColuna)
                    ? new RemoverDuplicatas(List.of(duplicataPorColuna))
                    : new RemoverDuplicatas());
        }

        List<ColunaForm> ordenadas = colunas.stream()
                .sorted(Comparator.comparingInt(ColunaForm::getOrdem))
                .toList();
        List<String> nomesOrdenados = ordenadas.stream().map(ColunaForm::getNome).toList();
        List<String> incluidas = ordenadas.stream().filter(ColunaForm::isIncluir).map(ColunaForm::getNome).toList();
        List<String> ordemOriginal = colunas.stream().map(ColunaForm::getNome).toList();

        if (incluidas.size() < colunas.size()) {
            regras.add(new SelecionarColunas(incluidas));
        } else if (!nomesOrdenados.equals(ordemOriginal)) {
            regras.add(new ReordenarColunas(nomesOrdenados));
        }

        for (ColunaForm c : ordenadas) {
            if (c.isIncluir() && temTexto(c.getNovoNome()) && !c.getNovoNome().trim().equals(c.getNome())) {
                regras.add(new RenomearColuna(c.getNome(), c.getNovoNome()));
            }
        }
        return regras;
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    public List<ColunaForm> getColunas() {
        return colunas;
    }

    public void setColunas(List<ColunaForm> colunas) {
        this.colunas = colunas;
    }

    public boolean isRemoverDuplicatas() {
        return removerDuplicatas;
    }

    public void setRemoverDuplicatas(boolean removerDuplicatas) {
        this.removerDuplicatas = removerDuplicatas;
    }

    public String getDuplicataPorColuna() {
        return duplicataPorColuna;
    }

    public void setDuplicataPorColuna(String duplicataPorColuna) {
        this.duplicataPorColuna = duplicataPorColuna;
    }

    public String getRemoverVaziosDaColuna() {
        return removerVaziosDaColuna;
    }

    public void setRemoverVaziosDaColuna(String removerVaziosDaColuna) {
        this.removerVaziosDaColuna = removerVaziosDaColuna;
    }

    public static class ColunaForm {
        private String nome;
        private boolean incluir;
        private int ordem;
        private String novoNome;
        private TipoPadronizacao padronizacao;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public boolean isIncluir() {
            return incluir;
        }

        public void setIncluir(boolean incluir) {
            this.incluir = incluir;
        }

        public int getOrdem() {
            return ordem;
        }

        public void setOrdem(int ordem) {
            this.ordem = ordem;
        }

        public String getNovoNome() {
            return novoNome;
        }

        public void setNovoNome(String novoNome) {
            this.novoNome = novoNome;
        }

        public TipoPadronizacao getPadronizacao() {
            return padronizacao;
        }

        public void setPadronizacao(TipoPadronizacao padronizacao) {
            this.padronizacao = padronizacao;
        }
    }
}
