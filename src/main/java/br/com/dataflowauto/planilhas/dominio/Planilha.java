package br.com.dataflowauto.planilhas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Conjunto de colunas e linhas lido de um arquivo. Imutável: cada regra de transformação
 * devolve uma nova planilha, o que permite desfazer etapas e testar regras isoladamente.
 */
public final class Planilha {

    /**
     * Teto de linhas de uma planilha lida ou gerada por combinação. As planilhas ficam em memória
     * na mesma JVM que serve a loja, então um arquivo enorme (ou uma junção por chave repetida,
     * que gera N×M linhas) não pode derrubar o sistema.
     */
    public static final int MAXIMO_LINHAS = 200_000;

    private final String nome;
    private final List<Coluna> colunas;
    private final List<Linha> linhas;

    public Planilha(String nome, List<Coluna> colunas, List<Linha> linhas) {
        this.nome = nome == null || nome.isBlank() ? "Planilha" : nome;
        this.colunas = List.copyOf(Objects.requireNonNull(colunas));
        this.linhas = List.copyOf(Objects.requireNonNull(linhas));
        validarColunasUnicas();
    }

    public static Planilha deNomes(String nome, List<String> nomesColunas, List<Linha> linhas) {
        return new Planilha(nome, nomesColunas.stream().map(Coluna::new).toList(), linhas);
    }

    public String getNome() {
        return nome;
    }

    public List<Coluna> getColunas() {
        return colunas;
    }

    public List<String> getNomesColunas() {
        return colunas.stream().map(Coluna::getNome).toList();
    }

    public List<Linha> getLinhas() {
        return linhas;
    }

    public int totalLinhas() {
        return linhas.size();
    }

    public int totalColunas() {
        return colunas.size();
    }

    public boolean possuiColuna(String nomeColuna) {
        return buscarColuna(nomeColuna).isPresent();
    }

    public Optional<Coluna> buscarColuna(String nomeColuna) {
        return colunas.stream().filter(c -> c.getNome().equals(nomeColuna)).findFirst();
    }

    public Coluna getColuna(String nomeColuna) {
        return buscarColuna(nomeColuna).orElseThrow(() ->
                new RegraNegocioException("A coluna \"" + nomeColuna + "\" não existe na planilha \"" + nome + "\"."));
    }

    /** Linhas de um intervalo, para paginação na pré-visualização. */
    public List<Linha> pagina(int numeroPagina, int tamanhoPagina) {
        int inicio = Math.max(0, (numeroPagina - 1) * tamanhoPagina);
        if (inicio >= linhas.size()) {
            return Collections.emptyList();
        }
        return linhas.subList(inicio, Math.min(linhas.size(), inicio + tamanhoPagina));
    }

    public int totalPaginas(int tamanhoPagina) {
        return Math.max(1, (int) Math.ceil(linhas.size() / (double) tamanhoPagina));
    }

    public Planilha comNome(String novoNome) {
        return new Planilha(novoNome, colunas, linhas);
    }

    public Planilha comLinhas(List<Linha> novasLinhas) {
        return new Planilha(nome, colunas, novasLinhas);
    }

    public Planilha comColunasELinhas(List<Coluna> novasColunas, List<Linha> novasLinhas) {
        return new Planilha(nome, novasColunas, novasLinhas);
    }

    public List<Linha> copiaDasLinhas() {
        return new ArrayList<>(linhas);
    }

    private void validarColunasUnicas() {
        Set<String> vistos = new HashSet<>();
        for (Coluna coluna : colunas) {
            if (!vistos.add(coluna.getNome())) {
                throw new RegraNegocioException("A coluna \"" + coluna.getNome() + "\" aparece mais de uma vez.");
            }
        }
    }
}
