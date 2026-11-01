package br.com.dataflowauto.comum.dominio;

/** Lançada quando uma operação viola uma regra de negócio (ex.: coluna inexistente). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
