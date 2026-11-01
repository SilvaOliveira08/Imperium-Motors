package br.com.dataflowauto.planilhas.dominio;

import br.com.dataflowauto.comum.dominio.TipoArquivo;

import java.io.OutputStream;

/** Grava uma {@link Planilha} em um formato de arquivo. */
public interface EscritorPlanilha {

    TipoArquivo formato();

    String tipoConteudo();

    void escrever(Planilha planilha, OutputStream destino);
}
