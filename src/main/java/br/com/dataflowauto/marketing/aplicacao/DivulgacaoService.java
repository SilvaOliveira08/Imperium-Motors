package br.com.dataflowauto.marketing.aplicacao;

import br.com.dataflowauto.comum.aplicacao.HistoricoService;
import br.com.dataflowauto.comum.dominio.CanalDivulgacao;
import br.com.dataflowauto.comum.dominio.RecursoNaoEncontradoException;
import br.com.dataflowauto.comum.dominio.TipoOperacao;
import br.com.dataflowauto.comum.web.DadosLoja;
import br.com.dataflowauto.marketing.dominio.GeradorTextos;
import br.com.dataflowauto.marketing.dominio.Publicacao;
import br.com.dataflowauto.marketing.dominio.TextoDivulgacao;
import br.com.dataflowauto.marketing.infra.PublicacaoRepository;
import br.com.dataflowauto.vendas.aplicacao.AnuncioService;
import br.com.dataflowauto.vendas.aplicacao.CatalogoDemonstracao;
import br.com.dataflowauto.vendas.dominio.Anuncio;
import br.com.dataflowauto.vendas.dominio.Proposta;
import br.com.dataflowauto.vendas.infra.ArmazenamentoFotos;
import br.com.dataflowauto.vendas.infra.PropostaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Divulgação dos carros: textos prontos por canal, fotos para baixar, registro de onde cada carro
 * está publicado e quantos interessados vieram de cada canal. Carro vendido ou fora da loja com
 * publicação ativa vira pendência ("tire do ar").
 */
@Service
public class DivulgacaoService {

    private final AnuncioService anuncios;
    private final PublicacaoRepository publicacoes;
    private final PropostaRepository propostas;
    private final ArmazenamentoFotos fotos;
    private final DadosLoja loja;
    private final HistoricoService historico;
    private final String enderecoSite;
    private final GeradorTextos gerador = new GeradorTextos();

    public DivulgacaoService(AnuncioService anuncios, PublicacaoRepository publicacoes, PropostaRepository propostas,
                             ArmazenamentoFotos fotos, DadosLoja loja, HistoricoService historico,
                             @Value("${dataflow.loja.site:http://localhost:8080}") String enderecoSite) {
        this.anuncios = anuncios;
        this.publicacoes = publicacoes;
        this.propostas = propostas;
        this.fotos = fotos;
        this.loja = loja;
        this.historico = historico;
        this.enderecoSite = enderecoSite.replaceAll("/+$", "");
    }

    /** Tudo o que a tela de divulgação de um carro mostra. */
    public record Kit(Anuncio anuncio, List<TextoDivulgacao> textos, Map<CanalDivulgacao, Publicacao> publicacoes,
                      Map<CanalDivulgacao, Long> contatosPorCanal, long contatosDiretos) {

        public List<Publicacao> pendencias() {
            return publicacoes.values().stream().filter(Publicacao::isPendenteRemocao).toList();
        }
    }

    /** Uma linha da visão geral: o carro, onde está publicado agora e quantos contatos vieram dos canais. */
    public record Linha(Anuncio anuncio, Map<CanalDivulgacao, Publicacao> ativas, long contatosDosCanais) {

        public boolean isPendente() {
            return ativas.values().stream().anyMatch(Publicacao::isPendenteRemocao);
        }
    }

    public record VisaoGeral(List<Linha> naLoja, List<Linha> pendentes, Map<CanalDivulgacao, Long> contatosPorCanal,
                             long contatosDiretos, Map<CanalDivulgacao, Long> carrosPorCanal) {
    }

    @Transactional(readOnly = true)
    public Kit kit(Long anuncioId) {
        Anuncio anuncio = anuncios.buscar(anuncioId);
        String link = enderecoSite + "/loja/carros/" + anuncio.getId();
        GeradorTextos.Vitrine vitrine = new GeradorTextos.Vitrine(loja.getNome(), loja.getEndereco(), loja.getCidade(),
                loja.temWhatsapp() ? loja.getWhatsappFormatado() : loja.getTelefone(), loja.getTaxaFormatada());
        List<TextoDivulgacao> textos = gerador.gerar(anuncio, vitrine, link, loja.parcelaDesde(anuncio.getPreco()));

        Map<CanalDivulgacao, Publicacao> porCanal = new EnumMap<>(CanalDivulgacao.class);
        publicacoes.findByAnuncioId(anuncioId).forEach(p -> porCanal.put(p.getCanal(), p));
        List<Proposta> contatos = propostas.findByAnuncioIdOrderByCriadaEmDesc(anuncioId);
        return new Kit(anuncio, textos, porCanal, contarPorCanal(contatos),
                contatos.stream().filter(p -> p.getOrigem() == null).count());
    }

    @Transactional(readOnly = true)
    public VisaoGeral visaoGeral() {
        Map<Long, List<Publicacao>> ativasPorAnuncio = publicacoes.findAll().stream().filter(Publicacao::isAtiva)
                .collect(Collectors.groupingBy(p -> p.getAnuncio().getId()));
        List<Proposta> todos = propostas.findAll();
        Map<Long, Long> contatosDosCanais = todos.stream().filter(p -> p.getOrigem() != null)
                .collect(Collectors.groupingBy(p -> p.getAnuncio().getId(), Collectors.counting()));

        Function<Anuncio, Linha> linha = a -> {
            Map<CanalDivulgacao, Publicacao> ativas = new EnumMap<>(CanalDivulgacao.class);
            ativasPorAnuncio.getOrDefault(a.getId(), List.of()).forEach(p -> ativas.put(p.getCanal(), p));
            return new Linha(a, ativas, contatosDosCanais.getOrDefault(a.getId(), 0L));
        };
        List<Linha> linhas = anuncios.listar().stream().map(linha).toList();
        List<Linha> naLoja = linhas.stream().filter(l -> l.anuncio().estaNaLoja())
                .sorted(Comparator.comparing((Linha l) -> l.ativas().size()).thenComparing(l -> l.anuncio().getTitulo()))
                .toList();
        List<Linha> pendentes = linhas.stream().filter(Linha::isPendente).toList();

        Map<CanalDivulgacao, Long> carrosPorCanal = new EnumMap<>(CanalDivulgacao.class);
        for (CanalDivulgacao canal : CanalDivulgacao.values()) {
            carrosPorCanal.put(canal, naLoja.stream().filter(l -> l.ativas().containsKey(canal)).count());
        }
        return new VisaoGeral(naLoja, pendentes, contarPorCanal(todos),
                todos.stream().filter(p -> p.getOrigem() == null).count(), carrosPorCanal);
    }

    @Transactional
    public void publicar(Long anuncioId, CanalDivulgacao canal, String link) {
        Anuncio anuncio = anuncios.buscar(anuncioId);
        Publicacao publicacao = publicacoes.findByAnuncioIdAndCanal(anuncioId, canal)
                .map(p -> {
                    p.publicar(link, LocalDate.now());
                    return p;
                })
                .orElseGet(() -> new Publicacao(anuncio, canal, link, LocalDate.now()));
        publicacoes.save(publicacao);
        historico.registrar(TipoOperacao.DIVULGACAO, anuncio.getTitulo() + " publicado no " + canal.getDescricao());
    }

    @Transactional
    public void tirarDoAr(Long anuncioId, CanalDivulgacao canal) {
        Publicacao publicacao = publicacoes.findByAnuncioIdAndCanal(anuncioId, canal)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Este carro não foi marcado como publicado no "
                        + canal.getDescricao() + "."));
        publicacao.tirarDoAr(LocalDate.now());
        publicacoes.save(publicacao);
        historico.registrar(TipoOperacao.DIVULGACAO, publicacao.getAnuncio().getTitulo() + " retirado do " + canal.getDescricao());
    }

    /** Todas as fotos do carro num .zip, na ordem do anúncio (a capa primeiro). */
    @Transactional(readOnly = true)
    public void fotosZip(Long anuncioId, OutputStream destino) {
        Anuncio anuncio = anuncios.buscar(anuncioId);
        String base = nomeArquivo(anuncio.getTitulo());
        try (ZipOutputStream zip = new ZipOutputStream(destino)) {
            int ordem = 1;
            for (String foto : anuncio.getFotos()) {
                zip.putNextEntry(new ZipEntry(base + "-" + ordem++ + foto.substring(foto.lastIndexOf('.'))));
                try (InputStream entrada = fotos.abrir(foto)) {
                    entrada.transferTo(zip);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível montar o arquivo de fotos.", e);
        }
    }

    public static String nomeArquivo(String titulo) {
        String semAcento = Normalizer.normalize(titulo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /**
     * Demonstração: marca os carros fictícios como publicados em canais variados, deixa um carro
     * vendido ainda no ar (para mostrar a pendência) e distribui a origem dos interessados.
     * Só mexe em carros fictícios que ainda não têm nenhuma publicação.
     */
    @Transactional
    public int carregarDemonstracao() {
        Set<String> placasDemo = CatalogoDemonstracao.placas();
        List<Anuncio> demo = anuncios.listar().stream()
                .filter(a -> placasDemo.contains(a.getVeiculo().getPlaca().valor()))
                .filter(a -> !publicacoes.existsByAnuncioId(a.getId()))
                .sorted(Comparator.comparing(a -> a.getVeiculo().getPlaca().valor()))
                .toList();
        CanalDivulgacao[] origens = {CanalDivulgacao.INSTAGRAM, CanalDivulgacao.WHATSAPP, CanalDivulgacao.FACEBOOK,
                CanalDivulgacao.OLX, null, CanalDivulgacao.WEBMOTORS, CanalDivulgacao.MARKETPLACE, CanalDivulgacao.INSTAGRAM};
        Anuncio vendidoMaisRecente = demo.stream().filter(Anuncio::estaVendido)
                .max(Comparator.comparing(a -> a.getVenda().getData())).orElse(null);
        int i = 0;
        int marcados = 0;
        for (Anuncio a : demo) {
            if (a.getStatus().name().equals("OCULTO")) {
                continue;
            }
            LocalDate inicio = a.getDataCompra() == null ? LocalDate.now().minusDays(10) : a.getDataCompra().plusDays(2);
            if (inicio.isAfter(LocalDate.now())) {
                inicio = LocalDate.now();
            }
            for (CanalDivulgacao canal : canaisDemonstracao(i)) {
                Publicacao p = new Publicacao(a, canal, null, inicio);
                if (a.estaVendido() && !Objects.equals(a, vendidoMaisRecente)) {
                    p.tirarDoAr(a.getVenda().getData());
                }
                publicacoes.save(p);
            }
            int j = i;
            for (Proposta proposta : propostas.findByAnuncioIdOrderByCriadaEmDesc(a.getId())) {
                if (proposta.getOrigem() == null) {
                    proposta.registrarOrigem(origens[(j++) % origens.length]);
                    propostas.save(proposta);
                }
            }
            i++;
            marcados++;
        }
        return marcados;
    }

    /** Variação de canais por carro: todos no Instagram e Facebook, os demais alternando. */
    private static List<CanalDivulgacao> canaisDemonstracao(int i) {
        List<CanalDivulgacao> canais = new ArrayList<>(List.of(CanalDivulgacao.INSTAGRAM, CanalDivulgacao.FACEBOOK));
        if (i % 2 == 0) {
            canais.add(CanalDivulgacao.MARKETPLACE);
        }
        if (i % 3 != 2) {
            canais.add(CanalDivulgacao.OLX);
        }
        if (i % 2 == 1) {
            canais.add(CanalDivulgacao.WEBMOTORS);
        }
        if (i % 4 == 0) {
            canais.add(CanalDivulgacao.WHATSAPP);
        }
        return canais;
    }

    private static Map<CanalDivulgacao, Long> contarPorCanal(List<Proposta> lista) {
        Map<CanalDivulgacao, Long> contagem = new EnumMap<>(CanalDivulgacao.class);
        for (CanalDivulgacao canal : CanalDivulgacao.values()) {
            contagem.put(canal, lista.stream().filter(p -> p.getOrigem() == canal).count());
        }
        return contagem;
    }
}
