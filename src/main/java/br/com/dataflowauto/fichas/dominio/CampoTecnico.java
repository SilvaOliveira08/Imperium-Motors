package br.com.dataflowauto.fichas.dominio;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo dos campos de uma ficha técnica. Cada campo sabe seu rótulo, seu grupo,
 * se é obrigatório para concluir a ficha e por quais nomes costuma aparecer em documentos.
 */
public enum CampoTecnico {
    MARCA("Marca", GrupoCampo.IDENTIFICACAO, true, "marca", "fabricante", "montadora"),
    MODELO("Modelo", GrupoCampo.IDENTIFICACAO, true, "modelo"),
    VERSAO("Versão", GrupoCampo.IDENTIFICACAO, false, "versao", "acabamento"),
    ANO_FABRICACAO("Ano de fabricação", GrupoCampo.IDENTIFICACAO, false, "ano fabricacao", "ano de fabricacao", "ano fab"),
    ANO_MODELO("Ano do modelo", GrupoCampo.IDENTIFICACAO, true, "ano modelo", "ano do modelo", "ano mod"),
    COR("Cor", GrupoCampo.IDENTIFICACAO, false, "cor", "cor predominante"),
    CHASSI("Chassi", GrupoCampo.IDENTIFICACAO, false, "chassi", "vin"),
    CARROCERIA("Carroceria", GrupoCampo.IDENTIFICACAO, false, "carroceria", "tipo de carroceria", "especie tipo"),
    MOTORIZACAO("Motorização", GrupoCampo.MOTOR, true, "motorizacao", "motor", "cilindrada"),
    POTENCIA("Potência", GrupoCampo.MOTOR, false, "potencia", "potencia maxima", "pot"),
    TORQUE("Torque", GrupoCampo.MOTOR, false, "torque", "torque maximo"),
    COMBUSTIVEL("Combustível", GrupoCampo.MOTOR, true, "combustivel"),
    CAMBIO("Câmbio", GrupoCampo.TRANSMISSAO, true, "cambio", "transmissao"),
    TRACAO("Tração", GrupoCampo.TRANSMISSAO, false, "tracao"),
    COMPRIMENTO("Comprimento", GrupoCampo.DIMENSOES, false, "comprimento"),
    LARGURA("Largura", GrupoCampo.DIMENSOES, false, "largura"),
    ALTURA("Altura", GrupoCampo.DIMENSOES, false, "altura"),
    ENTRE_EIXOS("Entre-eixos", GrupoCampo.DIMENSOES, false, "entre eixos", "entre-eixos", "distancia entre eixos"),
    PESO("Peso", GrupoCampo.DIMENSOES, false, "peso", "peso em ordem de marcha", "peso bruto"),
    PORTAS("Portas", GrupoCampo.CAPACIDADES, false, "portas", "numero de portas"),
    LUGARES("Lugares", GrupoCampo.CAPACIDADES, false, "lugares", "capacidade", "passageiros", "lotacao"),
    PORTA_MALAS("Porta-malas", GrupoCampo.CAPACIDADES, false, "porta malas", "porta-malas", "bagageiro"),
    PORTA_MALAS_7_LUGARES("Porta-malas com 7 lugares", GrupoCampo.CAPACIDADES, false, "porta malas 7 lugares", "porta-malas com 7 lugares", "porta malas com 7 lugares", "bagageiro 7 lugares"),
    TANQUE("Tanque", GrupoCampo.CAPACIDADES, false, "tanque", "capacidade do tanque", "tanque de combustivel"),
    CONSUMO_CIDADE("Consumo na cidade", GrupoCampo.CAPACIDADES, false, "consumo cidade", "consumo urbano", "consumo na cidade"),
    CONSUMO_ESTRADA("Consumo na estrada", GrupoCampo.CAPACIDADES, false, "consumo estrada", "consumo rodoviario", "consumo na estrada"),
    EQUIPAMENTOS("Equipamentos de série", GrupoCampo.EQUIPAMENTOS, false, "equipamentos", "equipamentos de serie", "itens de serie", "opcionais"),
    QUANTIDADE_DONOS("Número de donos", GrupoCampo.PROCEDENCIA, false, "numero de donos", "quantidade de donos", "donos", "qtd proprietarios"),
    ROUBO_FURTO("Roubo ou furto", GrupoCampo.PROCEDENCIA, false, "roubo furto", "roubo e furto", "roubo ou furto", "registro de roubo", "furto"),
    LEILAO("Leilão", GrupoCampo.PROCEDENCIA, false, "leilao", "passagem por leilao", "historico de leilao"),
    SINISTRO("Sinistro", GrupoCampo.PROCEDENCIA, false, "sinistro", "indicio de sinistro", "perda total", "sinistro perda total"),
    RESTRICOES("Restrições", GrupoCampo.PROCEDENCIA, false, "restricoes", "restricao", "gravame", "alienacao fiduciaria", "renajud", "bloqueios"),
    RECALL("Recall", GrupoCampo.PROCEDENCIA, false, "recall", "chamado de recall", "recalls pendentes"),
    RENAVAM("RENAVAM", GrupoCampo.DOCUMENTACAO, false, "renavam", "codigo renavam", "cod renavam"),
    MUNICIPIO_REGISTRO("Município de registro", GrupoCampo.DOCUMENTACAO, false, "municipio", "municipio uf", "local", "local de registro", "municipio de emplacamento"),
    PROPRIETARIO_ATUAL("Proprietário atual", GrupoCampo.DOCUMENTACAO, false, "proprietario", "proprietario atual", "nome do proprietario", "nome"),
    PROPRIETARIOS_ANTERIORES("Proprietários anteriores", GrupoCampo.DOCUMENTACAO, false, "proprietarios anteriores", "donos anteriores", "historico de proprietarios"),
    MULTAS("Multas", GrupoCampo.DOCUMENTACAO, false, "multas", "infracoes", "autuacoes", "multas pendentes"),
    DEBITOS("Débitos (IPVA, licenciamento)", GrupoCampo.DOCUMENTACAO, false, "debitos", "debitos ipva", "ipva", "licenciamento", "debitos do veiculo");

    private final String rotulo;
    private final GrupoCampo grupo;
    private final boolean obrigatorio;
    private final List<String> sinonimos;

    CampoTecnico(String rotulo, GrupoCampo grupo, boolean obrigatorio, String... sinonimos) {
        this.rotulo = rotulo;
        this.grupo = grupo;
        this.obrigatorio = obrigatorio;
        this.sinonimos = List.of(sinonimos);
    }

    public String getRotulo() {
        return rotulo;
    }

    public GrupoCampo getGrupo() {
        return grupo;
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }

    /** Pode aparecer na loja e no PDF entregue ao cliente. O chassi fica de fora, como os dados de proprietário. */
    public boolean isPublico() {
        return grupo.isPublico() && this != CHASSI;
    }

    /** Encontra o campo cujo nome em um documento corresponde ao rótulo informado. */
    public static Optional<CampoTecnico> porRotulo(String rotuloDocumento) {
        String alvo = normalizar(rotuloDocumento);
        return Arrays.stream(values())
                .filter(c -> c.sinonimos.contains(alvo) || normalizar(c.rotulo).equals(alvo) || normalizar(c.name()).equals(alvo))
                .findFirst();
    }

    public static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[_\\-/.]", " ").replaceAll("\\s+", " ").trim();
    }
}
