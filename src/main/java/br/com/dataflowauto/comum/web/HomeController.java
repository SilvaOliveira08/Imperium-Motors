package br.com.dataflowauto.comum.web;

import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.planilhas.aplicacao.PlanilhaService;
import br.com.dataflowauto.planilhas.dominio.EscritorPlanilha;
import br.com.dataflowauto.planilhas.dominio.Planilha;
import br.com.dataflowauto.marketing.aplicacao.DivulgacaoService;
import br.com.dataflowauto.vendas.aplicacao.DemonstracaoService;
import br.com.dataflowauto.vendas.aplicacao.PainelService;
import br.com.dataflowauto.vendas.aplicacao.PropostaService;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final HistoricoService historico;
    private final PlanilhaService planilhas;
    private final FichaService fichas;
    private final PainelService painel;
    private final PropostaService propostas;
    private final DemonstracaoService demonstracao;
    private final DivulgacaoService divulgacao;
    private final EscritorPlanilha escritorXlsx;

    public HomeController(HistoricoService historico, PlanilhaService planilhas, FichaService fichas,
                          PainelService painel, PropostaService propostas, DemonstracaoService demonstracao,
                          DivulgacaoService divulgacao,
                          java.util.List<EscritorPlanilha> escritores) {
        this.historico = historico;
        this.planilhas = planilhas;
        this.fichas = fichas;
        this.painel = painel;
        this.propostas = propostas;
        this.demonstracao = demonstracao;
        this.divulgacao = divulgacao;
        this.escritorXlsx = escritores.stream().filter(e -> e.formato() == br.com.dataflowauto.comum.dominio.TipoArquivo.XLSX)
                .findFirst().orElseThrow();
    }

    /** Painel da equipe: indicadores da loja e atalhos para os três módulos. */
    @GetMapping("/painel")
    public String inicio(Model model) {
        model.addAttribute("recentes", historico.recentes());
        model.addAttribute("totalPlanilhas", planilhas.listarArquivos().size());
        model.addAttribute("totalFichas", fichas.listar().size());
        model.addAttribute("indicadores", painel.indicadores());
        model.addAttribute("contatosNovos", propostas.listar(br.com.dataflowauto.vendas.dominio.StatusProposta.NOVA)
                .stream().limit(5).toList());
        return "index";
    }

    @PostMapping("/painel/demonstracao")
    public String carregarDemonstracao(RedirectAttributes atributos) {
        int criados = demonstracao.carregar();
        divulgacao.carregarDemonstracao();
        atributos.addFlashAttribute("sucesso", criados == 0
                ? "Os carros de demonstração já estavam carregados."
                : criados + " carro(s) de demonstração criados a partir das fichas técnicas.");
        return "redirect:/painel";
    }

    /** Relatórios em XLSX: estoque, despesas ou vendas. */
    @GetMapping("/painel/exportar/{relatorio}")
    public ResponseEntity<byte[]> exportar(@PathVariable String relatorio) {
        Planilha planilha = switch (relatorio) {
            case "estoque" -> painel.planilhaEstoque();
            case "despesas" -> painel.planilhaDespesas();
            case "vendas" -> painel.planilhaVendas();
            default -> throw new RecursoNaoEncontradoException("Relatório não encontrado.");
        };
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        escritorXlsx.escrever(planilha, saida);
        historico.registrar(TipoOperacao.EXPORTACAO, "Relatório de " + relatorio + " (" + planilha.totalLinhas() + " linhas)");
        String nome = relatorio + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType(escritorXlsx.tipoConteudo()))
                .body(saida.toByteArray());
    }

    @GetMapping("/entrar")
    public String entrar() {
        return "entrar";
    }

    @GetMapping("/historico")
    public String historico(Model model) {
        model.addAttribute("registros", historico.todos());
        return "historico";
    }
}
