package br.com.dataflowauto.planilhas.web;

import br.com.dataflowauto.comum.dominio.ArquivoImportado;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.dominio.TipoArquivo;
import br.com.dataflowauto.planilhas.aplicacao.PlanilhaService;
import br.com.dataflowauto.planilhas.dominio.EscritorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.planilhas.dominio.TipoJuncao;
import br.com.dataflowauto.planilhas.dominio.regras.RegraTransformacao;
import br.com.dataflowauto.planilhas.dominio.regras.TipoPadronizacao;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/planilhas")
public class PlanilhaController {

    private static final int LINHAS_POR_PAGINA = 50;

    private final PlanilhaService servico;
    private final AreaTrabalho areaTrabalho;

    public PlanilhaController(PlanilhaService servico, AreaTrabalho areaTrabalho) {
        this.servico = servico;
        this.areaTrabalho = areaTrabalho;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("arquivos", servico.listarArquivos());
        model.addAttribute("emEdicao", areaTrabalho.possuiPlanilha());
        return "planilhas/lista";
    }

    @PostMapping("/importar")
    public String importar(@RequestParam(name = "arquivos", required = false) List<MultipartFile> arquivos,
                           RedirectAttributes atributos) {
        List<ArquivoImportado> importados = servico.importar(arquivos);
        atributos.addFlashAttribute("sucesso", importados.size() == 1
                ? "Planilha \"" + importados.get(0).getNomeOriginal() + "\" importada."
                : importados.size() + " planilhas importadas.");
        if (importados.size() == 1) {
            return "redirect:/planilhas/" + importados.get(0).getId();
        }
        return "redirect:/planilhas";
    }

    @GetMapping("/{id}")
    public String visualizar(@PathVariable Long id, @RequestParam(defaultValue = "1") int pagina, Model model) {
        Planilha planilha = servico.carregar(id);
        model.addAttribute("arquivoId", id);
        adicionarVisualizacao(model, planilha, pagina);
        return "planilhas/visualizar";
    }

    @PostMapping("/{id}/editar")
    public String iniciarEdicao(@PathVariable Long id) {
        areaTrabalho.iniciar(servico.carregar(id));
        return "redirect:/planilhas/trabalho";
    }

    @GetMapping("/trabalho")
    public String trabalho(@RequestParam(defaultValue = "1") int pagina, Model model) {
        if (!areaTrabalho.possuiPlanilha()) {
            return "redirect:/planilhas";
        }
        Planilha atual = areaTrabalho.getAtual();
        adicionarVisualizacao(model, atual, pagina);
        model.addAttribute("form", OrganizacaoForm.de(atual));
        model.addAttribute("padronizacoes", TipoPadronizacao.values());
        model.addAttribute("tiposJuncao", TipoJuncao.values());
        model.addAttribute("arquivos", servico.listarArquivos());
        model.addAttribute("etapas", areaTrabalho.getEtapas());
        model.addAttribute("podeDesfazer", areaTrabalho.podeDesfazer());
        model.addAttribute("area", areaTrabalho.getId());
        return "planilhas/trabalho";
    }

    /** {@code area} identifica a planilha aberta quando a página foi gerada (ver {@link AreaTrabalho#conferir}). */
    @PostMapping("/trabalho/organizar")
    public String organizar(@ModelAttribute OrganizacaoForm form, @RequestParam(required = false) String area,
                            RedirectAttributes atributos) {
        areaTrabalho.conferir(area);
        List<RegraTransformacao> regras = form.paraRegras();
        if (regras.isEmpty()) {
            atributos.addFlashAttribute("erro", "Nenhuma alteração foi escolhida.");
            return "redirect:/planilhas/trabalho";
        }
        Planilha antes = areaTrabalho.getAtual();
        Planilha depois = servico.organizar(antes, regras);
        areaTrabalho.registrar(depois, new AreaTrabalho.Etapa(
                String.join("; ", regras.stream().map(RegraTransformacao::getDescricao).toList()),
                p -> servico.aplicarRegras(p, regras)));
        atributos.addFlashAttribute("sucesso", regras.size() + " regra(s) aplicada(s). Linhas: "
                + antes.totalLinhas() + " → " + depois.totalLinhas() + ".");
        return "redirect:/planilhas/trabalho";
    }

    @PostMapping("/trabalho/combinar")
    public String combinar(@RequestParam Long arquivoId, @RequestParam String chaveAtual,
                           @RequestParam String chaveOutra, @RequestParam TipoJuncao tipo,
                           @RequestParam(required = false) String area, RedirectAttributes atributos) {
        areaTrabalho.conferir(area);
        Planilha outra = servico.carregar(arquivoId);
        Planilha resultado = servico.combinar(areaTrabalho.getAtual(), outra, chaveAtual, chaveOutra, tipo);
        areaTrabalho.registrar(resultado, new AreaTrabalho.Etapa(
                "Combinada com " + outra.getNome() + " por " + chaveAtual + " = " + chaveOutra
                        + " (" + tipo.getDescricao().toLowerCase(Locale.ROOT) + ")",
                p -> servico.juntar(p, outra, chaveAtual, chaveOutra, tipo)));
        atributos.addFlashAttribute("sucesso", "Planilhas combinadas: " + resultado.totalLinhas() + " linhas.");
        return "redirect:/planilhas/trabalho";
    }

    @PostMapping("/trabalho/desfazer")
    public String desfazer(@RequestParam(required = false) String area, RedirectAttributes atributos) {
        areaTrabalho.conferir(area);
        areaTrabalho.desfazer();
        atributos.addFlashAttribute("sucesso", "Última etapa desfeita.");
        return "redirect:/planilhas/trabalho";
    }

    @GetMapping("/trabalho/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "xlsx") String formato,
                                           @RequestParam(required = false) String area) {
        areaTrabalho.conferir(area);
        TipoArquivo tipo = TipoArquivo.porNomeArquivo("x." + formato)
                .filter(TipoArquivo::ehPlanilha)
                .orElseThrow(() -> new RegraNegocioException("Formato de exportação inválido: " + formato));
        Planilha planilha = areaTrabalho.getAtual();
        EscritorPlanilha escritor = servico.escritorPara(tipo);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        servico.exportar(planilha, tipo, saida);
        String nomeArquivo = planilha.getNome().replaceAll("[^\\p{L}0-9 _+\\-]", "_") + "-organizada." + tipo.getExtensao();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nomeArquivo, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(escritor.tipoConteudo()))
                .body(saida.toByteArray());
    }

    @GetMapping("/api/{id}/colunas")
    @ResponseBody
    public List<String> colunas(@PathVariable Long id) {
        return servico.carregar(id).getNomesColunas();
    }

    private void adicionarVisualizacao(Model model, Planilha planilha, int pagina) {
        int totalPaginas = planilha.totalPaginas(LINHAS_POR_PAGINA);
        int paginaAtual = Math.min(Math.max(1, pagina), totalPaginas);
        model.addAttribute("planilha", planilha);
        model.addAttribute("linhas", planilha.pagina(paginaAtual, LINHAS_POR_PAGINA));
        model.addAttribute("pagina", paginaAtual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("primeiraLinha", (paginaAtual - 1) * LINHAS_POR_PAGINA + 1);
        model.addAttribute("qualidade", servico.analisar(planilha));
    }
}
