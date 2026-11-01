package br.com.dataflowauto.fichas.dominio;

/**
 * A fonte não conseguiu fazer a consulta (fora do ar, token vencido, tempo esgotado).
 * É diferente de consultar e não achar nada: nesse caso a fonte devolve um mapa vazio.
 */
public class FonteIndisponivelException extends RuntimeException {

    public FonteIndisponivelException(String fonte) {
        super(fonte + " indisponível");
    }
}
