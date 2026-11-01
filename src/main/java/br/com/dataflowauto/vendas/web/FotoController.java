package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.vendas.infra.ArmazenamentoFotos;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.TimeUnit;

@Controller
public class FotoController {

    private final ArmazenamentoFotos fotos;

    public FotoController(ArmazenamentoFotos fotos) {
        this.fotos = fotos;
    }

    @GetMapping("/fotos/{nome:.+}")
    public ResponseEntity<InputStreamResource> foto(@PathVariable String nome) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fotos.tipoConteudo(nome)))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new InputStreamResource(fotos.abrir(nome)));
    }
}
