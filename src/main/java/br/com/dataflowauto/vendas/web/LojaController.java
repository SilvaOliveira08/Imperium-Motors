package br.com.dataflowauto.vendas.web;

import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.RegraNegocioException;
import br.com.dataflowauto.comum.web.DadosLoja;
import br.com.dataflowauto.fichas.aplicacao.FichaService;
import br.com.dataflowauto.fichas.dominio.CampoFicha;
import br.com.dataflowauto.fichas.dominio.GrupoCampo;
import br.com.dataflowauto.fichas.dominio.StatusVerificacao;
import br.com.dataflowauto.vendas.aplicacao.AnuncioService;
import br.com.dataflowauto.vendas.aplicacao.PropostaService;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.Dinheiro;
import br.com.dataflowauto.vendas.dominio.FiltroLoja;
import br.com.dataflowauto.vendas.dominio.NovaProposta;
import br.com.dataflowauto.vendas.dominio.SimulacaoFinanciamento;
import br.com.dataflowauto.vendas.dominio.TipoProposta;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Site público da loja: catálogo, página do carro, simulação de financiamento e contato. */
@Controller
public class LojaController {

    /** Atributo da sessão com o canal de divulgação de onde o visitante chegou. */
    static final String ORIGEM_VISITA = "origemVisita";

    private final AnuncioService anuncios;
    private final PropostaService propostas;
    private final FichaService fichas;
    private final DadosLoja loja;

    public LojaController(AnuncioService anuncios, PropostaService propostas, FichaService fichas, DadosLoja loja) {
        this.anuncios = anuncios;
        this.propostas = propostas;
        this.fichas = fichas;
        this.loja = loja;
    }

    @GetMapping({"/", "/loja"})
    public String catalogo(@RequestParam(required = false) String busca,
                           @RequestParam(required = false) String marca,
                           @RequestParam(required = false) String combustivel,
                           @RequestParam(required = false) String cambio,
                           @RequestParam(required = false) String precoMaximo,
                           @RequestParam(required = false) String anoMinimo,
                           @RequestParam(required = false) String kmMaximo,
                           @RequestParam(required = false) String origem, HttpSession sessao,
                           @RequestParam(required = false) String ordem,
                           Model model) {
        lembrarOrigem(origem, sessao);
        FiltroLoja filtro;
        try {
            filtro = new FiltroLoja(busca, marca, combustivel, cambio, Dinheiro.interpretar(precoMaximo),
                    Conversoes.inteiro(anoMinimo, "ano"), Conversoes.inteiro(kmMaximo, "quilometragem"), ordem);
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            filtro = FiltroLoja.vazio();
        }
        AnuncioService.Catalogo catalogo = anuncios.catalogo(filtro);
        List<Anuncio> naLoja = catalogo.naLoja();
        model.addAttribute("filtro", filtro);
        model.addAttribute("anuncios", catalogo.resultado());
        model.addAttribute("totalNaLoja", naLoja.size());
        model.addAttribute("opcoes", catalogo.opcoes());
        model.addAttribute("destaques", filtro.ativo() ? List.of() : FiltroLoja.vazio().aplicar(naLoja).stream()
                .filter(Anuncio::isDestaque).limit(3).toList());
        Map<String, Long> porMarca = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        naLoja.stream().filter(a -> !a.getMarca().isBlank()).forEach(a -> porMarca.merge(a.getMarca(), 1L, Long::sum));
        model.addAttribute("marcas", porMarca);
        model.addAttribute("faixasPreco", java.util.stream.Stream.of(50000, 80000, 100000, 150000, 200000).map(BigDecimal::valueOf).toList());
        return "loja/catalogo";
    }

    @GetMapping("/loja/carros/{id}")
    public String detalhe(@PathVariable Long id,
                          @RequestParam(required = false) String entrada,
                          @RequestParam(required = false) Integer parcelas,
                          @RequestParam(required = false) String origem, HttpSession sessao,
                          Model model) {
        lembrarOrigem(origem, sessao);
        Anuncio anuncio = anuncios.buscarNaLoja(id);
        model.addAttribute("anuncio", anuncio);
        model.addAttribute("especificacoes", especificacoes(anuncio));
        model.addAttribute("semelhantes", anuncios.semelhantes(anuncio, 3));
        model.addAttribute("tipos", TipoProposta.values());

        BigDecimal entradaPadrao = anuncio.getPreco().multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP);
        int parcelasEscolhidas = parcelas == null ? 48 : parcelas;
        String entradaTexto = entrada == null ? entradaPadrao.toPlainString() : entrada;
        model.addAttribute("simEntrada", entradaTexto);
        model.addAttribute("simParcelas", parcelasEscolhidas);
        model.addAttribute("opcoesParcelas", List.of(12, 24, 36, 48, 60));
        try {
            model.addAttribute("simulacao", SimulacaoFinanciamento.calcular(anuncio.getPreco(),
                    Dinheiro.interpretar(entradaTexto), parcelasEscolhidas, loja.getTaxaFinanciamento()));
        } catch (RegraNegocioException e) {
            model.addAttribute("erroSimulacao", e.getMessage());
        }
        return "loja/detalhe";
    }

    @PostMapping("/loja/carros/{id}/contato")
    public String contato(@PathVariable Long id, @RequestParam(required = false) String tipo,
                          @RequestParam(required = false) String nome, @RequestParam(required = false) String telefone,
                          @RequestParam(required = false) String email, @RequestParam(required = false) String mensagem,
                          @RequestParam(required = false) String valorOferecido,
                          @RequestParam(required = false) String dataPreferida,
                          HttpSession sessao, RedirectAttributes atributos) {
        try {
            NovaProposta dados = new NovaProposta(Conversoes.opcao(TipoProposta.class, tipo), nome, telefone, email,
                    mensagem, Dinheiro.interpretar(valorOferecido), Conversoes.data(dataPreferida, "data preferida"));
            propostas.registrarPeloSite(id, dados, (CanalDivulgacao) sessao.getAttribute(ORIGEM_VISITA));
            atributos.addFlashAttribute("sucesso", "Recebemos seu contato, " + nome.trim().split("\\s+")[0]
                    + "! Nossa equipe responde em breve.");
        } catch (RegraNegocioException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
            atributos.addFlashAttribute("contato", Map.of("tipo", tipo == null ? "" : tipo, "nome", valor(nome),
                    "telefone", valor(telefone), "email", valor(email), "mensagem", valor(mensagem),
                    "valorOferecido", valor(valorOferecido), "dataPreferida", valor(dataPreferida)));
        }
        return "redirect:/loja/carros/" + id + "#contato";
    }

    /**
     * Links divulgados nos canais levam "?origem=instagram" (ou outro canal). A origem fica na sessão
     * do visitante, então mesmo navegando por outros carros o contato sai marcado com o canal certo.
     */
    private static void lembrarOrigem(String origem, HttpSession sessao) {
        CanalDivulgacao.porCodigo(origem).ifPresent(canal -> sessao.setAttribute(ORIGEM_VISITA, canal));
    }

    /** Ficha técnica em PDF do carro anunciado (sempre a versão concluída). */
    @GetMapping("/loja/carros/{id}/ficha-tecnica.pdf")
    public ResponseEntity<byte[]> fichaPdf(@PathVariable Long id) {
        Anuncio anuncio = anuncios.buscarNaLoja(id);
        if (!anuncio.estaNaLoja()) {
            throw new RecursoNaoEncontradoException("Ficha não disponível.");
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        fichas.gerarPdfPublico(anuncio.getFicha().getId(), saida);
        String nome = "ficha-tecnica-" + anuncio.getTitulo().toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nome).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(saida.toByteArray());
    }

    /** Só os campos confirmados da ficha, agrupados. A placa e o chassi não são publicados. */
    private static Map<GrupoCampo, List<CampoFicha>> especificacoes(Anuncio anuncio) {
        Map<GrupoCampo, List<CampoFicha>> resultado = new LinkedHashMap<>();
        anuncio.getFicha().camposPorGrupo().forEach((grupo, campos) -> {
            List<CampoFicha> visiveis = campos.stream()
                    .filter(c -> c.getStatus() == StatusVerificacao.CONFIRMADO)
                    .filter(c -> c.getCampo().isPublico())
                    .toList();
            if (!visiveis.isEmpty()) {
                resultado.put(grupo, visiveis);
            }
        });
        return resultado;
    }

    private static String valor(String s) {
        return s == null ? "" : s;
    }
}
