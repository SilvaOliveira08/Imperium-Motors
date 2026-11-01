package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.vendas.aplicacao.PropostaService;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Interessados que entraram em contato pela loja. */
@Controller
@RequestMapping("/propostas")
public class PropostaController {

    private final PropostaService propostas;

    public PropostaController(PropostaService propostas) {
        this.propostas = propostas;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String status, Model model) {
        StatusProposta filtro = Conversoes.opcao(StatusProposta.class, status);
        model.addAttribute("propostas", propostas.listar(filtro));
        model.addAttribute("filtro", filtro);
        model.addAttribute("situacoes", StatusProposta.values());
        return "propostas/lista";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam String status,
                         @RequestParam(required = false) String voltar, RedirectAttributes atributos) {
        propostas.alterarStatus(id, Conversoes.opcao(StatusProposta.class, status));
        atributos.addFlashAttribute("sucesso", "Situação do contato atualizada.");
        return voltar != null && voltar.matches("/anuncios/\\d+") ? "redirect:" + voltar + "#interessados" : "redirect:/propostas";
    }
}
