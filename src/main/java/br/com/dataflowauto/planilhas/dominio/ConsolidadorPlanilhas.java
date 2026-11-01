package br.com.dataflowauto.planilhas.dominio;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Combina duas planilhas a partir de uma coluna-chave de cada uma (CPF, placa, código...).
 * As chaves são comparadas sem diferenciar maiúsculas, espaços e pontuação, para que
 * "123.456.789-00" e "12345678900" sejam o mesmo CPF.
 */
public class ConsolidadorPlanilhas {

    private final int maximoLinhas;

    public ConsolidadorPlanilhas() {
        this(Planilha.MAXIMO_LINHAS);
    }

    /** Permite um teto menor (usado nos testes). */
    public ConsolidadorPlanilhas(int maximoLinhas) {
        this.maximoLinhas = maximoLinhas;
    }

    public Planilha combinar(Planilha primeira, Planilha segunda, String chavePrimeira, String chaveSegunda,
                             TipoJuncao tipo) {
        primeira.getColuna(chavePrimeira);
        segunda.getColuna(chaveSegunda);

        Map<String, String> nomesSegunda = nomesSemConflito(primeira, segunda, chaveSegunda);
        List<Coluna> colunas = new ArrayList<>(primeira.getColunas());
        nomesSegunda.values().forEach(n -> colunas.add(new Coluna(n)));

        Map<String, List<Linha>> indiceSegunda = new LinkedHashMap<>();
        for (Linha linha : segunda.getLinhas()) {
            String chave = normalizarChave(linha.getValor(chaveSegunda));
            if (!chave.isEmpty()) {
                indiceSegunda.computeIfAbsent(chave, k -> new ArrayList<>()).add(linha);
            }
        }

        List<Linha> resultado = new ArrayList<>();
        Set<String> chavesUsadas = new HashSet<>();
        for (Linha linhaA : primeira.getLinhas()) {
            String chave = normalizarChave(linhaA.getValor(chavePrimeira));
            List<Linha> correspondentes = chave.isEmpty() ? List.of() : indiceSegunda.getOrDefault(chave, List.of());
            if (correspondentes.isEmpty()) {
                if (tipo != TipoJuncao.INTERNA) {
                    adicionar(resultado, juntar(linhaA, null, nomesSegunda));
                }
                continue;
            }
            chavesUsadas.add(chave);
            for (Linha linhaB : correspondentes) {
                adicionar(resultado, juntar(linhaA, linhaB, nomesSegunda));
            }
        }

        if (tipo == TipoJuncao.COMPLETA) {
            for (Map.Entry<String, List<Linha>> entrada : indiceSegunda.entrySet()) {
                if (!chavesUsadas.contains(entrada.getKey())) {
                    for (Linha linhaB : entrada.getValue()) {
                        Map<String, String> valores = new LinkedHashMap<>();
                        valores.put(chavePrimeira, linhaB.getValor(chaveSegunda));
                        adicionar(resultado, juntar(new Linha(valores), linhaB, nomesSegunda));
                    }
                }
            }
            for (Linha linhaB : segunda.getLinhas()) {
                if (normalizarChave(linhaB.getValor(chaveSegunda)).isEmpty()) {
                    adicionar(resultado, juntar(new Linha(Map.of()), linhaB, nomesSegunda));
                }
            }
        }

        String nome = primeira.getNome() + " + " + segunda.getNome();
        return new Planilha(nome, colunas, resultado);
    }

    public static String normalizarChave(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("[\\s.\\-/]", "").toUpperCase(Locale.ROOT);
    }

    /** Mapeia cada coluna da segunda planilha (exceto a chave) para um nome que não colida com a primeira. */
    private Map<String, String> nomesSemConflito(Planilha primeira, Planilha segunda, String chaveSegunda) {
        Set<String> usados = new HashSet<>(primeira.getNomesColunas());
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String nome : segunda.getNomesColunas()) {
            if (nome.equals(chaveSegunda)) {
                continue;
            }
            String candidato = nome;
            if (usados.contains(candidato)) {
                candidato = nome + " (" + segunda.getNome() + ")";
            }
            int sufixo = 2;
            String base = candidato;
            while (usados.contains(candidato)) {
                candidato = base + " " + sufixo++;
            }
            usados.add(candidato);
            mapa.put(nome, candidato);
        }
        if (mapa.isEmpty() && segunda.totalColunas() <= 1) {
            throw new RegraNegocioException("A segunda planilha não tem colunas além da chave para combinar.");
        }
        return mapa;
    }

    /** A chave repetida nas duas planilhas multiplica as linhas (N×M): para antes de estourar a memória. */
    private void adicionar(List<Linha> resultado, Linha linha) {
        if (resultado.size() == maximoLinhas) {
            throw new RegraNegocioException("A combinação passaria de " + maximoLinhas + " linhas. "
                    + "Confira se a coluna-chave escolhida não se repete muito nas duas planilhas.");
        }
        resultado.add(linha);
    }

    private Linha juntar(Linha linhaA, Linha linhaB, Map<String, String> nomesSegunda) {
        Map<String, String> valores = new LinkedHashMap<>(linhaA.getValores());
        for (Map.Entry<String, String> entrada : nomesSegunda.entrySet()) {
            valores.put(entrada.getValue(), linhaB == null ? "" : linhaB.getValor(entrada.getKey()));
        }
        return new Linha(valores);
    }
}
