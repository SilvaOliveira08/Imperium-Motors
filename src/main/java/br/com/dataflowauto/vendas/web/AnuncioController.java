package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.vendas.aplicacao.AnuncioService;
import br.com.dataflowauto.vendas.aplicacao.DespesaService;
import br.com.dataflowauto.vendas.aplicacao.PropostaService;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.CategoriaDespesa;
import br.com.dataflowauto.vendas.dominio.Dinheiro;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.Venda;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/** Estoque da loja (área da equipe): anúncios, fotos, custos, situação e venda de cada carro. */
@Controller
@RequestMapping("/anuncios")
public class AnuncioController {

    private final AnuncioService anuncios;
    private final DespesaService despesas;
    private final PropostaService propostas;

    public AnuncioController(AnuncioService anuncios, DespesaService despesas, PropostaService propostas) {
        this.anuncios = anuncios;
        this.despesas = despesas;
        this.propostas = propostas;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("anuncios", anuncios.listar());
        model.addAttribute("fichasProntas", anuncios.fichasProntasParaAnunciar());
        return "anuncios/lista";
    }

    @GetMapping("/novo")
    public String novo(@RequestParam(required = false) Long ficha, Model model) {
        List<FichaTecnica> prontas = anuncios.fichasProntasParaAnunciar();
        model.addAttribute("fichasProntas", prontas);
        model.addAttribute("fichaSelecionada", ficha);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AnuncioForm());
        }
        return "anuncios/novo";
    }

    @PostMapping
    public String criar(@RequestParam(required = false) Long fichaId, @ModelAttribute AnuncioForm form,
                        @RequestParam(required = false) List<MultipartFile> fotos, RedirectAttributes atributos) {
        if (fichaId == null) {
            throw new RegraNegocioException("Escolha a ficha técnica do carro.");
        }
        try {
            Anuncio anuncio = anuncios.criar(fichaId, form.paraDados(), fotos);
            atributos.addFlashAttribute("sucesso", "Carro anunciado. Ele já aparece na loja.");
            return "redirect:/anuncios/" + anuncio.getId();
        } catch (RegraNegocioException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
            atributos.addFlashAttribute("form", form);
            return "redirect:/anuncios/novo?ficha=" + fichaId;
        }
    }

    @GetMapping("/{id}")
    public String gerenciar(@PathVariable Long id, Model model) {
        Anuncio anuncio = anuncios.buscar(id);
        model.addAttribute("anuncio", anuncio);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", AnuncioForm.de(anuncio));
        }
        model.addAttribute("despesas", despesas.doAnuncio(id));
        model.addAttribute("rentabilidade", anuncios.rentabilidade(id));
        model.addAttribute("propostas", propostas.doAnuncio(id));
        model.addAttribute("categorias", CategoriaDespesa.values());
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("situacoes", List.of(StatusAnuncio.DISPONIVEL, StatusAnuncio.RESERVADO, StatusAnuncio.OCULTO));
        model.addAttribute("hoje", LocalDate.now());
        return "anuncios/gerenciar";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @ModelAttribute AnuncioForm form, RedirectAttributes atributos) {
        try {
            anuncios.atualizar(id, form.paraDados());
            atributos.addFlashAttribute("sucesso", "Dados do anúncio salvos.");
        } catch (RegraNegocioException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
            atributos.addFlashAttribute("form", form);
        }
        return "redirect:/anuncios/" + id;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam String status, RedirectAttributes atributos) {
        Anuncio anuncio = anuncios.alterarStatus(id, Conversoes.opcao(StatusAnuncio.class, status));
        atributos.addFlashAttribute("sucesso", "Situação alterada para \"" + anuncio.getStatus().getDescricao() + "\".");
        return "redirect:/anuncios/" + id;
    }

    @PostMapping("/{id}/fotos")
    public String adicionarFotos(@PathVariable Long id, @RequestParam(required = false) List<MultipartFile> fotos,
                                 RedirectAttributes atributos) {
        anuncios.adicionarFotos(id, fotos);
        atributos.addFlashAttribute("sucesso", "Fotos adicionadas.");
        return "redirect:/anuncios/" + id + "#fotos";
    }

    @PostMapping("/{id}/fotos/{indice}/remover")
    public String removerFoto(@PathVariable Long id, @PathVariable int indice, RedirectAttributes atributos) {
        anuncios.removerFoto(id, indice);
        atributos.addFlashAttribute("sucesso", "Foto removida.");
        return "redirect:/anuncios/" + id + "#fotos";
    }

    @PostMapping("/{id}/fotos/{indice}/capa")
    public String definirCapa(@PathVariable Long id, @PathVariable int indice, RedirectAttributes atributos) {
        anuncios.definirCapa(id, indice);
        atributos.addFlashAttribute("sucesso", "Capa do anúncio atualizada.");
        return "redirect:/anuncios/" + id + "#fotos";
    }

    @PostMapping("/{id}/despesas")
    public String adicionarDespesa(@PathVariable Long id, @RequestParam String data, @RequestParam String categoria,
                                   @RequestParam(required = false) String descricao, @RequestParam String valor,
                                   RedirectAttributes atributos) {
        despesas.adicionar(id, Conversoes.data(data, "data"), Conversoes.opcao(CategoriaDespesa.class, categoria),
                descricao, Dinheiro.interpretar(valor));
        atributos.addFlashAttribute("sucesso", "Despesa lançada.");
        return "redirect:/anuncios/" + id + "#custos";
    }

    @PostMapping("/{id}/despesas/{despesaId}/remover")
    public String removerDespesa(@PathVariable Long id, @PathVariable Long despesaId, RedirectAttributes atributos) {
        despesas.remover(id, despesaId);
        atributos.addFlashAttribute("sucesso", "Despesa removida.");
        return "redirect:/anuncios/" + id + "#custos";
    }

    @PostMapping("/{id}/venda")
    public String vender(@PathVariable Long id, @RequestParam String comprador,
                         @RequestParam(required = false) String documento, @RequestParam String valor,
                         @RequestParam String data, @RequestParam String formaPagamento,
                         @RequestParam(required = false) Long propostaId, RedirectAttributes atributos) {
        Venda venda = new Venda(comprador, documento, Dinheiro.interpretar(valor), Conversoes.data(data, "data da venda"),
                Conversoes.opcao(FormaPagamento.class, formaPagamento));
        anuncios.registrarVenda(id, venda, propostaId);
        atributos.addFlashAttribute("sucesso", "Venda registrada. O carro saiu da loja.");
        return "redirect:/anuncios/" + id;
    }

    @PostMapping("/{id}/venda/desfazer")
    public String desfazerVenda(@PathVariable Long id, RedirectAttributes atributos) {
        anuncios.desfazerVenda(id);
        atributos.addFlashAttribute("sucesso", "Venda desfeita. O carro ficou fora da loja até você publicá-lo de novo.");
        return "redirect:/anuncios/" + id;
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        anuncios.excluir(id);
        atributos.addFlashAttribute("sucesso", "Anúncio excluído.");
        return "redirect:/anuncios";
    }

    @PostMapping("/despesas/importar")
    public String importarDespesas(@RequestParam("arquivo") MultipartFile arquivo, RedirectAttributes atributos) {
        DespesaService.ResultadoImportacao resultado = despesas.importar(arquivo);
        if (resultado.importadas() == 0) {
            atributos.addFlashAttribute("erro", "Nenhuma despesa importada.");
        } else {
            atributos.addFlashAttribute("sucesso", resultado.importadas() + " despesa(s) importada(s).");
        }
        atributos.addFlashAttribute("problemasImportacao", resultado.problemas());
        return "redirect:/anuncios";
    }
}
