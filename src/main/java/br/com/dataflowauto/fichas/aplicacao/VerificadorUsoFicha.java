package br.com.dataflowauto.fichas.aplicacao;

import br.com.dataflowauto.fichas.dominio.FichaTecnica;

import java.util.Optional;

/**
 * Permite que outros módulos (como a loja) impeçam a exclusão de uma ficha que estão usando,
 * sem que o módulo de fichas precise conhecê-los.
 */
public interface VerificadorUsoFicha {

    /** Motivo para não excluir a ficha, ou vazio se ela pode ser excluída. */
    Optional<String> impedimentoParaExcluir(FichaTecnica ficha);
}
