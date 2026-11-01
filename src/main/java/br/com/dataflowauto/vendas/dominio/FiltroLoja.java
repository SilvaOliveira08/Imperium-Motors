package br.com.dataflowauto.vendas.dominio;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Filtros e ordenação do catálogo público. Campos vazios não filtram. */
public record FiltroLoja(String busca, String marca, String combustivel, String cambio,
                         BigDecimal precoMaximo, Integer anoMinimo, Integer kmMaximo, String ordem) {

    public static FiltroLoja vazio() {
        return new FiltroLoja(null, null, null, null, null, null, null, null);
    }

    public List<Anuncio> aplicar(List<Anuncio> anuncios) {
        return anuncios.stream().filter(this::aceita).sorted(comparador()).toList();
    }

    public boolean aceita(Anuncio a) {
        return contem(busca, a.getTitulo() + " " + a.getMarca() + " " + a.getModelo() + " " + a.dado(CampoTecnico.VERSAO))
                && igual(marca, a.getMarca())
                && igual(combustivel, a.getCombustivel())
                && igual(cambio, a.getTipoCambio())
                && (precoMaximo == null || a.getPreco().compareTo(precoMaximo) <= 0)
                && (kmMaximo == null || a.getQuilometragem() <= kmMaximo)
                && (anoMinimo == null || ano(a) >= anoMinimo);
    }

    public boolean ativo() {
        Predicate<String> preenchido = s -> s != null && !s.isBlank();
        return preenchido.test(busca) || preenchido.test(marca) || preenchido.test(combustivel)
                || preenchido.test(cambio) || precoMaximo != null || anoMinimo != null || kmMaximo != null;
    }

    private Comparator<Anuncio> comparador() {
        Comparator<Anuncio> recentes = Comparator.comparing(Anuncio::getCriadoEm).reversed();
        if (ordem == null) {
            return Comparator.comparing(Anuncio::isDestaque).reversed().thenComparing(recentes);
        }
        return switch (ordem) {
            case "menor-preco" -> Comparator.comparing(Anuncio::getPreco);
            case "maior-preco" -> Comparator.comparing(Anuncio::getPreco).reversed();
            case "menor-km" -> Comparator.comparing(Anuncio::getQuilometragem);
            case "mais-novo" -> Comparator.comparingInt(FiltroLoja::ano).reversed().thenComparing(recentes);
            default -> Comparator.comparing(Anuncio::isDestaque).reversed().thenComparing(recentes);
        };
    }

    private static int ano(Anuncio a) {
        try {
            return Integer.parseInt(a.getAnoModelo().replaceAll("\\D", "").substring(0, 4));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean contem(String termo, String texto) {
        if (termo == null || termo.isBlank()) {
            return true;
        }
        String alvo = CampoTecnico.normalizar(texto);
        for (String parte : CampoTecnico.normalizar(termo).split(" ")) {
            if (!alvo.contains(parte)) {
                return false;
            }
        }
        return true;
    }

    private static boolean igual(String filtro, String valor) {
        return filtro == null || filtro.isBlank() || CampoTecnico.normalizar(filtro).equals(CampoTecnico.normalizar(valor));
    }
}
