package br.com.dataflowauto.fichas.web;

import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.aplicacao.NovaFichaComando;
import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.FichaTecnica;
import br.com.dataflowauto.fichas.dominio.StatusVerificacao;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

@Controller
@RequestMapping("/fichas")
public class FichaController {

    private final FichaService servico;

    public FichaController(FichaService servico) {
        this.servico = servico;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("fichas", servico.listar());
        return "fichas/lista";
    }

    @GetMapping("/nova")
    public String nova() {
        return "fichas/nova";
    }

    @PostMapping
    public String criar(@RequestParam String placa,
                        @RequestParam(required = false) MultipartFile documento,
                        @RequestParam(required = false) String marca,
                        @RequestParam(required = false) String modelo,
                        @RequestParam(required = false) String anoModelo,
                        @RequestParam(required = false) String renavam,
                        RedirectAttributes atributos) {
        Map<CampoTecnico, String> informados = new EnumMap<>(CampoTecnico.class);
        adicionarSePreenchido(informados, CampoTecnico.MARCA, marca);
        adicionarSePreenchido(informados, CampoTecnico.MODELO, modelo);
        adicionarSePreenchido(informados, CampoTecnico.ANO_MODELO, anoModelo);
        adicionarSePreenchido(informados, CampoTecnico.RENAVAM, renavam);

        FichaTecnica ficha = servico.criar(new NovaFichaComando(placa, documento, informados));
        long preenchidos = ficha.getCampos().stream().filter(c -> c.possuiValor()).count();
        atributos.addFlashAttribute("sucesso", "Ficha criada com " + preenchidos + " campo(s) preenchido(s). "
                + "Revise os campos a confirmar antes de concluir.");
        avisarSeIncompleta(ficha, atributos);
        return "redirect:/fichas/" + ficha.getId();
    }

    @GetMapping("/{id}")
    public String revisar(@PathVariable Long id, Model model) {
        FichaTecnica ficha = servico.buscar(id);
        model.addAttribute("ficha", ficha);
        model.addAttribute("grupos", ficha.camposPorGrupo());
        model.addAttribute("pendentes", ficha.camposPendentes());
        model.addAttribute("confirmados", ficha.contar(StatusVerificacao.CONFIRMADO));
        model.addAttribute("aConfirmar", ficha.contar(StatusVerificacao.A_CONFIRMAR));
        model.addAttribute("ausentes", ficha.contar(StatusVerificacao.AUSENTE));
        return "fichas/revisar";
    }

    @PostMapping("/{id}")
    public String salvarRevisao(@PathVariable Long id, @ModelAttribute RevisaoForm form, RedirectAttributes atributos) {
        try {
            FichaTecnica ficha = servico.revisar(id, form.valoresPorCampo(), form.camposConfirmados(), form.querConcluir());
            atributos.addFlashAttribute("sucesso", form.querConcluir()
                    ? "Ficha concluída. O PDF já pode ser gerado como versão final."
                    : "Revisão salva. " + ficha.camposPendentes().size() + " pendência(s).");
        } catch (RegraNegocioException e) {
            // salva o que foi revisado e só informa por que não concluiu
            servico.revisar(id, form.valoresPorCampo(), form.camposConfirmados(), false);
            atributos.addFlashAttribute("erro", "Revisão salva, mas a ficha não foi concluída. " + e.getMessage());
        }
        return "redirect:/fichas/" + id;
    }

    @PostMapping("/{id}/consultar")
    public String reconsultar(@PathVariable Long id, RedirectAttributes atributos) {
        int novos = servico.reconsultar(id);
        FichaTecnica ficha = servico.buscar(id);
        if (ficha.consultaIncompleta()) {
            // "nada novo" aqui seria mentira: pelo menos uma fonte nem chegou a responder
            avisarSeIncompleta(ficha, atributos);
            if (novos > 0) {
                atributos.addFlashAttribute("sucesso", novos + " informação(ões) nova(s) encontrada(s) nas fontes que responderam.");
            }
        } else {
            atributos.addFlashAttribute("sucesso", novos == 0
                    ? "Consulta refeita: nenhuma informação nova encontrada."
                    : novos + " informação(ões) nova(s) encontrada(s). Confira e confirme abaixo.");
        }
        return "redirect:/fichas/" + id;
    }

    private static void avisarSeIncompleta(FichaTecnica ficha, RedirectAttributes atributos) {
        if (ficha.consultaIncompleta()) {
            atributos.addFlashAttribute("erro", "Uma fonte de consulta não respondeu (veja \"Fontes consultadas\"). "
                    + "Multas, débitos e restrições podem não ter sido verificados: tente consultar novamente mais tarde.");
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        FichaTecnica ficha = servico.buscar(id);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        servico.gerarPdf(id, saida);
        String nome = "ficha-tecnica-" + ficha.getVeiculo().getPlaca().valor() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nome).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(saida.toByteArray());
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        servico.excluir(id);
        atributos.addFlashAttribute("sucesso", "Ficha excluída.");
        return "redirect:/fichas";
    }

    private static void adicionarSePreenchido(Map<CampoTecnico, String> mapa, CampoTecnico campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            mapa.put(campo, valor.trim());
        }
    }
}
