package br.com.dataflowauto.fichas.dominio;

import java.util.Map;

/**
 * Uma fonte de onde o sistema pode tirar dados técnicos: um documento enviado,
 * uma API autorizada, uma base interna. O serviço de preenchimento percorre todas
 * as fontes disponíveis sem saber como cada uma funciona.
 */
public interface FonteDadosVeiculo {

    /** Nome exibido ao usuário para indicar de onde veio a informação. */
    String nome();

    OrigemDado origem();

    /** Indica se a fonte está habilitada e consegue responder a esta consulta. */
    boolean podeAtender(ConsultaVeiculo consulta);

    /**
     * Valores encontrados. Campos não encontrados simplesmente não aparecem no mapa.
     *
     * @throws FonteIndisponivelException se a consulta não pôde ser feita
     */
    Map<CampoTecnico, String> buscar(ConsultaVeiculo consulta);
}
