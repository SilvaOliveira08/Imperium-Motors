package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.fichas.aplicacao.VerificadorUsoFicha;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.vendas.infra.AnuncioRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Impede excluir uma ficha técnica que já virou anúncio (os dados do carro vêm dela). */
@Component
public class UsoFichaPelaLoja implements VerificadorUsoFicha {

    private final AnuncioRepository anuncios;

    public UsoFichaPelaLoja(AnuncioRepository anuncios) {
        this.anuncios = anuncios;
    }

    @Override
    public Optional<String> impedimentoParaExcluir(FichaTecnica ficha) {
        return anuncios.findByFichaId(ficha.getId()).isEmpty()
                ? Optional.empty()
                : Optional.of("Esta ficha é usada por um anúncio da loja. Exclua o anúncio antes de excluir a ficha.");
    }
}
