package br.com.dataflowauto.fichas.aplicacao;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Dados para iniciar uma ficha: placa obrigatória, documento opcional e valores
 * que o usuário já quis informar no cadastro.
 */
public record NovaFichaComando(String placa, MultipartFile documento, Map<CampoTecnico, String> valoresInformados) {

    public NovaFichaComando {
        valoresInformados = valoresInformados == null ? Map.of() : Map.copyOf(valoresInformados);
    }

    public boolean possuiDocumento() {
        return documento != null && !documento.isEmpty();
    }
}
