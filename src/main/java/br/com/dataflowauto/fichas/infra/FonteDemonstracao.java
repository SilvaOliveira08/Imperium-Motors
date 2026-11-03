package br.com.dataflowauto.fichas.infra;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.fichas.dominio.ConsultaVeiculo;
import br.com.dataflowauto.fichas.dominio.FonteDadosVeiculo;
import br.com.dataflowauto.fichas.dominio.OrigemDado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static br.com.dataflowauto.fichas.dominio.CampoTecnico.*;

/**
 * Base fictícia que simula uma consulta veicular por placa (dados técnicos, procedência,
 * multas, débitos e proprietários), usada para demonstrar o fluxo enquanto não houver uma
 * API autorizada. Pode ser desligada em application.properties.
 * Os dados ficam sempre "a confirmar", como qualquer fonte externa. Nomes e números são inventados.
 */
@Component
@Order(3)
public class FonteDemonstracao implements FonteDadosVeiculo {

    private static final String NADA_CONSTA = "Nada consta";
    private static final String LOJA = "Imperium Motors Ltda";
    private static final String EM_DIA = "IPVA 2026 e licenciamento 2026 pagos";

    private static final Map<String, Map<CampoTecnico, String>> BASE = new HashMap<>();

    static {
        // Fichas técnicas completas: digitar só a placa já preenche tudo.
        tecnica("ABC1D23",
                MARCA, "Volkswagen", MODELO, "Polo", VERSAO, "Highline 200 TSI", ANO_FABRICACAO, "2023",
                ANO_MODELO, "2024", MOTORIZACAO, "1.0 TSI turbo, 3 cilindros", POTENCIA, "116 cv (etanol)",
                TORQUE, "16,8 kgfm", COMBUSTIVEL, "Flex", CAMBIO, "Automático de 6 marchas",
                TRACAO, "Dianteira", COMPRIMENTO, "4.074 mm", LARGURA, "1.751 mm", ALTURA, "1.468 mm",
                ENTRE_EIXOS, "2.566 mm", PORTA_MALAS, "300 L", TANQUE, "52 L", PORTAS, "4", LUGARES, "5");
        tecnica("BRA2E19",
                MARCA, "Fiat", MODELO, "Strada", VERSAO, "Volcano 1.3", ANO_FABRICACAO, "2022",
                ANO_MODELO, "2023", MOTORIZACAO, "1.3 Firefly, 4 cilindros", POTENCIA, "109 cv (etanol)",
                COMBUSTIVEL, "Flex", CAMBIO, "CVT", TRACAO, "Dianteira", CARROCERIA, "Picape cabine dupla",
                PORTAS, "4", LUGARES, "5");
        tecnica("XYZ9876",
                MARCA, "Chevrolet", MODELO, "Onix", VERSAO, "LT 1.0", ANO_FABRICACAO, "2020",
                ANO_MODELO, "2020", MOTORIZACAO, "1.0, 3 cilindros", COMBUSTIVEL, "Flex",
                CAMBIO, "Manual de 6 marchas");
        tecnica("CVC9Y21",
                MARCA, "Chevrolet", MODELO, "Cruze", VERSAO, "LTZ 1.4 Turbo", ANO_FABRICACAO, "2020",
                ANO_MODELO, "2021", CARROCERIA, "Sedã", MOTORIZACAO, "1.4 turbo, 4 cilindros", POTENCIA, "153 cv (etanol)",
                TORQUE, "24,5 kgfm", COMBUSTIVEL, "Flex", CAMBIO, "Automático de 6 marchas", TRACAO, "Dianteira",
                PORTAS, "4", LUGARES, "5", PORTA_MALAS, "440 L", TANQUE, "52 L",
                CONSUMO_CIDADE, "10,8 km/l", CONSUMO_ESTRADA, "13,9 km/l",
                EQUIPAMENTOS, "Teto solar, MyLink, OnStar, alerta de colisão, bancos em couro");
        tecnica("SAV4Z50",
                MARCA, "Mitsubishi", MODELO, "Pajero Sport", VERSAO, "HPE-S 2.4 4x4 Diesel", ANO_FABRICACAO, "2020",
                ANO_MODELO, "2021", CARROCERIA, "SUV", MOTORIZACAO, "2.4 turbodiesel, 4 cilindros", POTENCIA, "190 cv",
                TORQUE, "43,9 kgfm", COMBUSTIVEL, "Diesel", CAMBIO, "Automático de 8 marchas", TRACAO, "4x4 Super Select II",
                PORTAS, "4", LUGARES, "7", PORTA_MALAS, "502 L", PORTA_MALAS_7_LUGARES, "131 L", TANQUE, "68 L", CONSUMO_CIDADE, "8,9 km/l", CONSUMO_ESTRADA, "10,7 km/l",
                EQUIPAMENTOS, "7 lugares, bancos em couro, câmera 360°, teto solar, controle de descida");
        tecnica("VRT1A23",
                MARCA, "Volkswagen", MODELO, "Virtus", VERSAO, "Highline 200 TSI", ANO_FABRICACAO, "2022",
                ANO_MODELO, "2023", CARROCERIA, "Sedã", MOTORIZACAO, "1.0 TSI turbo, 3 cilindros", POTENCIA, "128 cv (etanol)",
                TORQUE, "20,4 kgfm", COMBUSTIVEL, "Flex", CAMBIO, "Automático de 6 marchas", TRACAO, "Dianteira",
                PORTAS, "4", LUGARES, "5", PORTA_MALAS, "521 L", TANQUE, "52 L",
                CONSUMO_CIDADE, "11,4 km/l", CONSUMO_ESTRADA, "14,1 km/l",
                EQUIPAMENTOS, "VW Play, painel digital, ACC, frenagem autônoma, carregador por indução");
        tecnica("CMD2B34",
                MARCA, "Jeep", MODELO, "Commander", VERSAO, "Limited 1.3 T270", ANO_FABRICACAO, "2022",
                ANO_MODELO, "2023", CARROCERIA, "SUV 7 lugares", MOTORIZACAO, "1.3 turbo T270, 4 cilindros", POTENCIA, "185 cv (etanol)",
                TORQUE, "27,5 kgfm", COMBUSTIVEL, "Flex", CAMBIO, "Automático de 6 marchas", TRACAO, "Dianteira",
                PORTAS, "4", LUGARES, "7", PORTA_MALAS, "661 L", PORTA_MALAS_7_LUGARES, "233 L", TANQUE, "60 L",
                CONSUMO_CIDADE, "9,2 km/l", CONSUMO_ESTRADA, "11,3 km/l",
                EQUIPAMENTOS, "Teto panorâmico, som Harman Kardon, ADAS nível 2, bancos ventilados");

        // Procedência, documentação e débitos de todos os carros da demonstração.
        historico("ABC1D23", "1", LOJA, "Carla Regina Souza (2023–2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, "Alienação fiduciária baixada em 2026 (financiamento quitado)", NADA_CONSTA, NADA_CONSTA);
        historico("BRA2E19", "1", LOJA, "Construtora Horizonte Ltda (2022–2026)", "Valinhos/SP",
                "1 multa paga em 2025: excesso de velocidade até 20% (R$ 130,16)", EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("XYZ9876", "2", "João da Silva", "Marcos Antunes (2020–2023); Patrícia Lemos (2023–2026); " + LOJA + " (2026)", "Hortolândia/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("RTC4B21", "1", LOJA, "Henrique Vasconcelos (2022–2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, "Recall do airbag do motorista realizado em 2024", NADA_CONSTA);
        historico("QWE7F45", "2", LOJA, "Roberta Pinheiro (2020–2022); Felipe Antunes (2022–2026)", "São Paulo/SP",
                "2 multas pagas: rodízio (R$ 130,16) e estacionamento irregular (R$ 195,23)", EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("JPC2A87", "1", LOJA, "Daniela Freitas (2022–2026)", "Sumaré/SP",
                NADA_CONSTA, EM_DIA, "Alienação fiduciária baixada em 2026", "Recall do módulo do freio realizado em 2023", NADA_CONSTA);
        historico("HBZ1C09", "1", LOJA, "Mateus Oliveira (2021–2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("CRT5D33", "1", LOJA, "Luciana Prado (2023–2026)", "Indaiatuba/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("KWD8E12", "2", LOJA, "Locadora Rota Certa Ltda (2022–2024); Wesley Martins (2024–2026)", "Campinas/SP",
                "1 multa paga em 2024: avanço de sinal vermelho (R$ 293,47)", EM_DIA, NADA_CONSTA, NADA_CONSTA,
                "Pequena colisão no para-choque em 2024, reparada (sem dano estrutural)");
        historico("ARG3G56", "1", LOJA, "Simone Carvalho (2021–2026)", "Paulínia/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("TCR6H90", "1", LOJA, "Ricardo Gomes (2021–2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, "Alienação fiduciária baixada em 2026", NADA_CONSTA, NADA_CONSTA);
        historico("HLX9J14", "1", LOJA, "Fazenda Santa Clara Agropecuária (2020–2026)", "Mogi Mirim/SP",
                "1 multa em aberto: transitar no acostamento (R$ 195,23) — será paga pela loja antes da venda",
                EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("TRK4K28", "1", LOJA, "Juliana Castro (2022–2026)", "Americana/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("KCK7L63", "3", LOJA, "Igor Fernandes (2021–2022); Mônica Reis (2022–2024); Tatiane Brito (2024–2026)", "Jundiaí/SP",
                "1 multa paga em 2023: excesso de velocidade (R$ 130,16)", EM_DIA, NADA_CONSTA, NADA_CONSTA,
                "Indício de média monta em 2022, reparado; laudo cautelar aprovado com apontamento");
        historico("TOR2M71", "1", LOJA, "Transportes Bandeirantes Ltda (2021–2026)", "Limeira/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, "Recall do sensor de airbag pendente — agendado na concessionária", NADA_CONSTA);
        historico("GOL5N36", "3", LOJA, "Antônio Ferreira (2019–2021); Bruna Lopes (2021–2023); Cleber Santos (2023–2026)", "Campinas/SP",
                "2 multas em aberto: estacionamento irregular (R$ 195,23) e celular ao volante (R$ 293,47)",
                "Licenciamento 2026 em aberto (R$ 160,22)", NADA_CONSTA, NADA_CONSTA,
                "Batida leve na traseira em 2023, em reparo pela loja");
        historico("HRV3P48", "1", LOJA, "Eliane Matos (2022–2026)", "Vinhedo/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("BYD1Q52", "1", LOJA, "Gabriel Tanaka (2023–2026)", "Campinas/SP",
                NADA_CONSTA, "Isento de IPVA (veículo elétrico); licenciamento 2026 pago", NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("PGT6R19", "1", "Ana Paula Moreira", "Rogério Batista (2022–2026); " + LOJA + " (2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("SDZ4S77", "2", "Agropecuária Boa Vista Ltda", "Sítio Três Irmãos (2019–2023); Alberto Campos (2023–2026); " + LOJA + " (2026)", "Itapira/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("DST8T05", "1", "Diego Santana", "Cristina Alves (2021–2026); " + LOJA + " (2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("RGR2U83", "2", "Paulo Roberto Lima", "Serviços Elétricos Volt Ltda (2019–2022); Nelson Prates (2022–2026); " + LOJA + " (2026)", "Sumaré/SP",
                "1 multa paga em 2022: excesso de peso (R$ 130,16)", EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("YRS5V31", "1", "Renata Figueiredo", "Fábio Moreira (2021–2026); " + LOJA + " (2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("NVS7W64", "1", "Vanessa Albuquerque", "Leandro Siqueira (2022–2026); " + LOJA + " (2026)", "Valinhos/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("MOB3X97", "1", "Igor Batista", "Priscila Duarte (2022–2026); " + LOJA + " (2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("CVC9Y21", "2", "Rodrigo Menezes", "Aline Barros (2020–2023)", "Campinas/SP",
                "3 multas: 2 pagas e 1 em aberto — avanço de sinal (R$ 293,47)",
                "IPVA 2026 em aberto: 2 parcelas (R$ 1.420,00)", "Alienação fiduciária ATIVA — Banco Exemplo S.A.",
                NADA_CONSTA, NADA_CONSTA);
        historico("SPN6C42", "1", LOJA, "Rafaela Guimarães (2022–2026)", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("SAV4Z50", "1", "Mineração Serra Alta Ltda", "—", "Poços de Caldas/MG",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("VRT1A23", "1", "Sandra Teixeira", "—", "Campinas/SP",
                NADA_CONSTA, EM_DIA, NADA_CONSTA, NADA_CONSTA, NADA_CONSTA);
        historico("CMD2B34", "1", "Otávio Nascimento", "—", "Ribeirão Preto/SP",
                "1 multa em aberto: excesso de velocidade até 20% (R$ 130,16)", EM_DIA,
                "Alienação fiduciária ATIVA — financiamento em andamento", NADA_CONSTA, NADA_CONSTA);
    }

    private final boolean habilitada;

    public FonteDemonstracao(@Value("${dataflow.fichas.fonte-demonstracao:true}") boolean habilitada) {
        this.habilitada = habilitada;
    }

    @Override
    public String nome() {
        return "Base de demonstração (dados fictícios)";
    }

    @Override
    public OrigemDado origem() {
        return OrigemDado.FONTE_EXTERNA;
    }

    @Override
    public boolean podeAtender(ConsultaVeiculo consulta) {
        return habilitada && BASE.containsKey(consulta.placa().valor());
    }

    @Override
    public Map<CampoTecnico, String> buscar(ConsultaVeiculo consulta) {
        return BASE.getOrDefault(consulta.placa().valor(), Map.of());
    }

    private static void tecnica(String placa, Object... pares) {
        Map<CampoTecnico, String> mapa = dadosDa(placa);
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((CampoTecnico) pares[i], (String) pares[i + 1]);
        }
    }

    private static void historico(String placa, String donos, String proprietarioAtual, String anteriores, String municipio,
                                  String multas, String debitos, String restricoes, String recall, String sinistro) {
        Map<CampoTecnico, String> mapa = dadosDa(placa);
        mapa.put(QUANTIDADE_DONOS, donos);
        mapa.put(ROUBO_FURTO, NADA_CONSTA);
        mapa.put(LEILAO, NADA_CONSTA);
        mapa.put(SINISTRO, sinistro);
        mapa.put(RESTRICOES, restricoes);
        mapa.put(RECALL, recall);
        mapa.put(RENAVAM, renavamFicticio(placa));
        mapa.put(MUNICIPIO_REGISTRO, municipio);
        mapa.put(PROPRIETARIO_ATUAL, proprietarioAtual);
        mapa.put(PROPRIETARIOS_ANTERIORES, anteriores);
        mapa.put(MULTAS, multas);
        mapa.put(DEBITOS, debitos);
    }

    private static Map<CampoTecnico, String> dadosDa(String placa) {
        return BASE.computeIfAbsent(placa, p -> new EnumMap<>(CampoTecnico.class));
    }

    private static String renavamFicticio(String placa) {
        return String.format("%011d", Math.floorMod((long) placa.hashCode() * 2654435761L, 100_000_000_000L));
    }
}
