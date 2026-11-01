package br.com.dataflowauto.marketing.web;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.marketing.aplicacao.DivulgacaoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Área de divulgação do painel: visão geral dos canais e o kit (textos, fotos, publicações) de cada carro. */
@Controller
@RequestMapping("/divulgacao")
public class DivulgacaoController {

    private final DivulgacaoService servico;

    public DivulgacaoController(DivulgacaoService servico) {
        this.servico = servico;
    }

    @GetMapping
    public String visaoGeral(Model model) {
        model.addAttribute("visao", servico.visaoGeral());
        model.addAttribute("canais", CanalDivulgacao.values());
        return "divulgacao/lista";
    }

    @GetMapping("/{anuncioId}")
    public String kit(@PathVariable Long anuncioId, Model model) {
        model.addAttribute("kit", servico.kit(anuncioId));
        model.addAttribute("canais", CanalDivulgacao.values());
        return "divulgacao/kit";
    }

    @PostMapping("/{anuncioId}/canais/{canal}")
    public String publicar(@PathVariable Long anuncioId, @PathVariable String canal,
                           @RequestParam(required = false) String link, RedirectAttributes atributos) {
        CanalDivulgacao escolhido = canal(canal);
        try {
            servico.publicar(anuncioId, escolhido, link);
            atributos.addFlashAttribute("sucesso", "Marcado como publicado no " + escolhido.getDescricao() + ".");
        } catch (RegraNegocioException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/divulgacao/" + anuncioId + "#canal-" + escolhido.getCodigo();
    }

    @PostMapping("/{anuncioId}/canais/{canal}/tirar")
    public String tirarDoAr(@PathVariable Long anuncioId, @PathVariable String canal,
                            @RequestParam(required = false) String voltar, RedirectAttributes atributos) {
        CanalDivulgacao escolhido = canal(canal);
        try {
            servico.tirarDoAr(anuncioId, escolhido);
            atributos.addFlashAttribute("sucesso", "Marcado como retirado do " + escolhido.getDescricao() + ".");
        } catch (RegraNegocioException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
        }
        return "lista".equals(voltar) ? "redirect:/divulgacao" : "redirect:/divulgacao/" + anuncioId + "#canal-" + escolhido.getCodigo();
    }

    @GetMapping("/{anuncioId}/fotos.zip")
    public ResponseEntity<StreamingResponseBody> fotos(@PathVariable Long anuncioId) {
        String nome = DivulgacaoService.nomeArquivo(servico.kit(anuncioId).anuncio().getTitulo()) + "-fotos.zip";
        StreamingResponseBody corpo = saida -> servico.fotosZip(anuncioId, saida);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(corpo);
    }

    private static CanalDivulgacao canal(String codigo) {
        return CanalDivulgacao.porCodigo(codigo).orElseThrow(() -> new RecursoNaoEncontradoException("Canal desconhecido."));
    }
}
