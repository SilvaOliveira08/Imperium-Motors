package br.com.dataflowauto.comum.dominio;

/** Lançada quando um arquivo enviado não passa na validação (tipo, tamanho ou conteúdo). */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public ArquivoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
