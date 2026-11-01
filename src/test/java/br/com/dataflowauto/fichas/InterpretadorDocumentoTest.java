package br.com.dataflowauto.fichas;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.InterpretadorDocumento;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretadorDocumentoTest {

    private final InterpretadorDocumento interpretador = new InterpretadorDocumento();

    @Test
    void reconheceRotulosComSinonimosEAcentos() {
        String texto = """
                FICHA DO VEÍCULO
                Fabricante: Toyota
                Modelo: Corolla
                Versão: XEi 2.0
                Motor: 2.0 Dynamic Force
                Potência máxima: 177 cv
                Câmbio = CVT
                Combustível\tFlex
                Comprimento    4.630 mm
                Observação: sem rótulo conhecido
                """;
        Map<CampoTecnico, String> r = interpretador.interpretar(texto);
        assertThat(r)
                .containsEntry(CampoTecnico.MARCA, "Toyota")
                .containsEntry(CampoTecnico.MODELO, "Corolla")
                .containsEntry(CampoTecnico.VERSAO, "XEi 2.0")
                .containsEntry(CampoTecnico.MOTORIZACAO, "2.0 Dynamic Force")
                .containsEntry(CampoTecnico.POTENCIA, "177 cv")
                .containsEntry(CampoTecnico.CAMBIO, "CVT")
                .containsEntry(CampoTecnico.COMBUSTIVEL, "Flex")
                .containsEntry(CampoTecnico.COMPRIMENTO, "4.630 mm")
                .hasSize(8);
    }

    @Test
    void entendeCamposCombinadosDoCrlv() {
        String texto = """
                MARCA/MODELO/VERSÃO: VW/GOL 1.0 MI
                ANO FAB/MOD: 2019/2020
                COR PREDOMINANTE: BRANCA
                CHASSI: 9BWAA05U0KT000000
                """;
        Map<CampoTecnico, String> r = interpretador.interpretar(texto);
        assertThat(r)
                .containsEntry(CampoTecnico.MARCA, "VW")
                .containsEntry(CampoTecnico.MODELO, "GOL 1.0 MI")
                .containsEntry(CampoTecnico.ANO_FABRICACAO, "2019")
                .containsEntry(CampoTecnico.ANO_MODELO, "2020")
                .containsEntry(CampoTecnico.COR, "BRANCA")
                .containsEntry(CampoTecnico.CHASSI, "9BWAA05U0KT000000");
    }

    @Test
    void textoSemDadosNaoGeraNada() {
        assertThat(interpretador.interpretar("Lorem ipsum dolor sit amet")).isEmpty();
        assertThat(interpretador.interpretar(null)).isEmpty();
    }
}
