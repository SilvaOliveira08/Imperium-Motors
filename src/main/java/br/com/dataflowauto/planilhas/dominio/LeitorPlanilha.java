package br.com.dataflowauto.planilhas.dominio;

import br.com.dataflowauto.comum.dominio.TipoArquivo;

import java.io.InputStream;

/** Converte o conteúdo de um arquivo em uma {@link Planilha}. Cada formato tem sua implementação. */
public interface LeitorPlanilha {

    boolean suporta(TipoArquivo tipo);

    Planilha ler(InputStream conteudo, String nomePlanilha);
}
