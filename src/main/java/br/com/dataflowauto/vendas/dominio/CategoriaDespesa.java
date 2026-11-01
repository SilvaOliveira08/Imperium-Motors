package br.com.dataflowauto.vendas.dominio;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Categorias de custo de um carro em estoque. Os sinônimos permitem importar
 * lançamentos de planilhas (inclusive da planilha de Controle de Despesas) sem
 * exigir que o nome da categoria seja digitado exatamente igual.
 */
public enum CategoriaDespesa {
    MECANICA("Mecânica", "mecanica", "manutencao", "manutencao preventiva", "manutencao corretiva", "troca de oleo", "revisao"),
    FUNILARIA_PINTURA("Funilaria e pintura", "funilaria", "pintura", "lanternagem"),
    ESTETICA("Estética e lavagem", "estetica", "lavagem", "polimento", "higienizacao", "lava rapido"),
    PNEUS("Pneus", "pneu", "pneus", "alinhamento", "balanceamento"),
    DOCUMENTACAO("Documentação", "documentacao", "transferencia", "despachante", "vistoria", "cartorio"),
    IPVA_LICENCIAMENTO("IPVA e licenciamento", "ipva", "licenciamento", "ipva e licenciamento", "seguro"),
    COMBUSTIVEL("Combustível", "combustivel", "gasolina", "etanol", "abastecimento"),
    MULTAS("Multas", "multa", "multas"),
    ANUNCIOS("Anúncios e marketing", "anuncio", "anuncios", "marketing", "publicidade", "fotos"),
    COMISSAO("Comissão", "comissao", "corretagem"),
    OPERACIONAL("Operacional", "pedagio", "pedagios", "estacionamento", "frete", "guincho", "operacional"),
    OUTROS("Outros", "outros", "outro", "diversos");

    private final String descricao;
    private final List<String> sinonimos;

    CategoriaDespesa(String descricao, String... sinonimos) {
        this.descricao = descricao;
        this.sinonimos = List.of(sinonimos);
    }

    public String getDescricao() {
        return descricao;
    }

    /** Interpreta o nome vindo de uma planilha. Categoria desconhecida vira OUTROS. */
    public static CategoriaDespesa interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return OUTROS;
        }
        String alvo = normalizar(texto);
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(texto.trim())
                        || normalizar(c.descricao).equals(alvo)
                        || c.sinonimos.contains(alvo))
                .findFirst()
                .orElse(OUTROS);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[_\\-/]", " ").replaceAll("\\s+", " ").trim();
    }
}
