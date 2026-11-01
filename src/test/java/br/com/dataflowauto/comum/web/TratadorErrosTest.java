package br.com.dataflowauto.comum.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class TratadorErrosTest {

    private final TratadorErros tratador = new TratadorErros(DataSize.ofMegabytes(10), DataSize.ofMegabytes(50));

    private static MockHttpServletRequest vindoDe(String pagina) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/planilhas/importar");
        request.addHeader("Referer", "http://localhost" + pagina);
        return request;
    }

    @Test
    void envioGrandeDemaisMostraOsLimitesDaConfiguracao() {
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        String destino = tratador.arquivoGrande(vindoDe("/planilhas"), atributos);

        assertThat(destino).isEqualTo("redirect:/planilhas");
        assertThat((String) atributos.getFlashAttributes().get("erro"))
                .contains("10 MB por arquivo").contains("50 MB por envio");
    }

    @Test
    void outraFalhaNoUploadNaoViraTamanhoEVaiParaOLog(CapturedOutput saida) {
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        // ex.: o sistema operacional apagou a pasta temporária do Tomcat
        tratador.falhaNoEnvio(new MultipartException("pasta temporária não existe"), vindoDe("/planilhas"), atributos);

        assertThat((String) atributos.getFlashAttributes().get("erro"))
                .contains("Não foi possível receber o arquivo")
                .doesNotContain("tamanho");
        assertThat(saida.getOut()).contains("Falha ao receber arquivo").contains("pasta temporária não existe");
    }
}
