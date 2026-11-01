package br.com.dataflowauto.planilhas.dominio.regras;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.planilhas.dominio.Coluna;
import br.com.dataflowauto.planilhas.dominio.Linha;
import br.com.dataflowauto.planilhas.dominio.Planilha;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mantém apenas as colunas escolhidas, na ordem em que foram informadas. */
public class SelecionarColunas extends RegraTransformacao {

    private final List<String> colunas;

    public SelecionarColunas(List<String> colunas) {
        super("Selecionar colunas: " + String.join(", ", colunas));
        if (colunas.isEmpty()) {
            throw new RegraNegocioException("Selecione pelo menos uma coluna.");
        }
        this.colunas = List.copyOf(colunas);
    }

    @Override
    protected void validar(Planilha planilha) {
        colunas.forEach(planilha::getColuna);
    }

    @Override
    protected Planilha transformar(Planilha planilha) {
        List<Linha> linhas = planilha.getLinhas().stream().map(linha -> {
            Map<String, String> valores = new LinkedHashMap<>();
            colunas.forEach(c -> valores.put(c, linha.getValor(c)));
            return new Linha(valores);
        }).toList();
        return planilha.comColunasELinhas(colunas.stream().map(Coluna::new).toList(), linhas);
    }
}
