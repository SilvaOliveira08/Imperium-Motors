package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Coluna;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RenomearColuna extends RegraTransformacao {

    private final String nomeAtual;
    private final String novoNome;

    public RenomearColuna(String nomeAtual, String novoNome) {
        super("Renomear \"" + nomeAtual + "\" para \"" + novoNome + "\"");
        if (novoNome == null || novoNome.isBlank()) {
            throw new RegraNegocioException("O novo nome da coluna \"" + nomeAtual + "\" não pode ser vazio.");
        }
        this.nomeAtual = nomeAtual;
        this.novoNome = novoNome.trim();
    }

    @Override
    protected void validar(Planilha planilha) {
        planilha.getColuna(nomeAtual);
        if (!nomeAtual.equals(novoNome) && planilha.possuiColuna(novoNome)) {
            throw new RegraNegocioException("Já existe uma coluna chamada \"" + novoNome + "\".");
        }
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        List<Coluna> colunas = planilha.getColunas().stream()
                .map(c -> c.getNome().equals(nomeAtual) ? c.renomear(novoNome) : c)
                .toList();
        List<Linha> linhas = planilha.getLinhas().stream().map(linha -> {
            Map<String, String> valores = new LinkedHashMap<>();
            linha.getValores().forEach((k, v) -> valores.put(k.equals(nomeAtual) ? novoNome : k, v));
            return new Linha(valores);
        }).toList();
        return planilha.comColunasELinhas(colunas, linhas);
    }
}
