package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteDadosVeiculo;
import br.com.dataflowauto.fichas.dominio.InterpretadorDocumento;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Dados extraídos do documento enviado pelo usuário. É a primeira fonte consultada. */
@Component
@Order(1)
public class FonteDocumento implements FonteDadosVeiculo {

    private final InterpretadorDocumento interpretador = new InterpretadorDocumento();

    @Override
    public String nome() {
        return "Documento enviado";
    }

    @Override
    public OrigemDado origem() {
        return OrigemDado.DOCUMENTO;
    }

    @Override
    public boolean podeAtender(ConsultaVeiculo consulta) {
        return consulta.possuiDocumento();
    }

    @Override
    public Map<CampoTecnico, String> buscar(ConsultaVeiculo consulta) {
        return interpretador.interpretar(consulta.textoDocumento());
    }
}
