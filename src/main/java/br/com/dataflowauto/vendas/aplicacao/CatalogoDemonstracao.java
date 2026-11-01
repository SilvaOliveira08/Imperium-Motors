package br.com.dataflowauto.vendas.aplicacao;

import br.com.dataflowauto.fichas.dominio.CampoTecnico;
import br.com.dataflowauto.vendas.dominio.CategoriaDespesa;
import br.com.dataflowauto.vendas.dominio.FormaPagamento;
import br.com.dataflowauto.vendas.dominio.StatusAnuncio;
import br.com.dataflowauto.vendas.dominio.StatusProposta;
import br.com.dataflowauto.vendas.dominio.TipoProposta;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.dataflowauto.fichas.dominio.CampoTecnico.*;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.ANUNCIOS;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.COMISSAO;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.DOCUMENTACAO;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.ESTETICA;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.FUNILARIA_PINTURA;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.IPVA_LICENCIAMENTO;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.MECANICA;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.OPERACIONAL;
import static br.com.dataflowauto.vendas.dominio.CategoriaDespesa.PNEUS;

/**
 * Estoque fictício usado pela demonstração: carros com ficha técnica completa, custos,
 * interessados e vendas espalhadas pelos últimos meses para preencher o painel.
 * Datas são relativas ao dia do carregamento ("dias atrás"), então os números sempre parecem atuais.
 * Placas, nomes, telefones e documentos são inventados.
 */
public final class CatalogoDemonstracao {

    /** Ordem dos valores aceitos por {@link #ficha(String...)}. */
    private static final List<CampoTecnico> ORDEM_FICHA = List.of(MARCA, MODELO, VERSAO, ANO_FABRICACAO, ANO_MODELO,
            CARROCERIA, MOTORIZACAO, POTENCIA, TORQUE, COMBUSTIVEL, CAMBIO, TRACAO, PORTAS, LUGARES, PORTA_MALAS,
            TANQUE, CONSUMO_CIDADE, CONSUMO_ESTRADA, EQUIPAMENTOS);

    private CatalogoDemonstracao() {
    }

    record Gasto(int diasAtras, CategoriaDespesa categoria, String descricao, int valor) {
    }

    /** Interessado; {@code diasTestDrive} é a data pedida a partir de hoje, {@code status} é onde o atendimento parou. */
    record Contato(TipoProposta tipo, String nome, String telefone, String email, String mensagem,
                   Integer valorOferecido, Integer diasTestDrive, StatusProposta status) {
    }

    /** Fechamento; se {@code peloPrimeiroContato}, o primeiro interessado vira a venda. */
    record VendaDemo(String comprador, String documento, int valor, int diasAtras, FormaPagamento forma,
                     boolean peloPrimeiroContato) {
    }

    record CarroDemo(String placa, String cor, Map<CampoTecnico, String> ficha, int preco, int quilometragem,
                     int valorCompra, int diasDesdeCompra, String descricao, boolean destaque,
                     List<Gasto> gastos, List<Contato> contatos, StatusAnuncio situacao, VendaDemo venda) {
    }

    /** Fichas que ficam em revisão, para mostrar o passo antes do anúncio. */
    record FichaPendente(String placa, Map<CampoTecnico, String> valores) {
    }

    static List<CarroDemo> carros() {
        return List.of(
                // ---------- na loja ----------
                new CarroDemo("RTC4B21", "Prata Supernova",
                        ficha("Toyota", "Corolla", "XEi 2.0", "2022", "2023", "Sedã", "2.0 Dynamic Force, 4 cilindros",
                                "177 cv (etanol)", "21,4 kgfm", "Flex", "CVT com 10 marchas simuladas", "Dianteira", "4", "5",
                                "470 L", "50 L", "11,2 km/l", "13,6 km/l",
                                "Multimídia 9\" com CarPlay, câmera de ré, piloto automático adaptativo, 7 airbags, faróis full LED"),
                        129900, 38200, 112000, 30,
                        "Sedã mais confiável do Brasil, com todas as revisões feitas na Toyota. Laudo cautelar 100% aprovado, sem passagem por leilão.",
                        true,
                        List.of(new Gasto(27, ESTETICA, "Higienização interna e vitrificação", 690),
                                new Gasto(25, DOCUMENTACAO, "Transferência e vistoria", 640)),
                        List.of(new Contato(TipoProposta.FINANCIAMENTO, "Patrícia Andrade", "19992341187", "patricia.andrade@example.com",
                                        "Quero dar 40 mil de entrada e financiar o resto em 48x.", null, null, StatusProposta.EM_ATENDIMENTO),
                                new Contato(TipoProposta.TEST_DRIVE, "Rafael Monteiro", "19981237744", null,
                                        "Consigo ir depois das 18h?", null, 2, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("QWE7F45", "Preto Cristal",
                        ficha("Honda", "Civic", "Touring 1.5 Turbo", "2020", "2021", "Sedã", "1.5 turbo, 4 cilindros",
                                "173 cv", "22,4 kgfm", "Gasolina", "CVT", "Dianteira", "4", "5",
                                "525 L", "56 L", "10,9 km/l", "13,4 km/l",
                                "Teto solar, bancos em couro, Honda Sensing, som premium, partida por botão"),
                        139900, 52400, 121000, 45,
                        "Topo de linha, teto solar e pacote Honda Sensing. Pneus Michelin com 80% de vida útil. Aceitamos troca.",
                        true,
                        List.of(new Gasto(42, MECANICA, "Revisão dos 50 mil km com troca de fluidos", 1480),
                                new Gasto(40, PNEUS, "Alinhamento e balanceamento", 220),
                                new Gasto(39, ANUNCIOS, "Ensaio fotográfico", 280)),
                        List.of(new Contato(TipoProposta.PROPOSTA, "Eduardo Lins", "11987654410", "edu.lins@example.com",
                                "Pago 130 mil à vista hoje.", 130000, null, StatusProposta.EM_ATENDIMENTO)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("JPC2A87", "Cinza Granite",
                        ficha("Jeep", "Compass", "Longitude T270", "2022", "2022", "SUV", "1.3 turbo T270, 4 cilindros",
                                "185 cv (etanol)", "27,5 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "5",
                                "410 L", "60 L", "9,6 km/l", "11,8 km/l",
                                "Painel digital, multimídia 10\", frenagem autônoma, sensor de estacionamento, rodas aro 18"),
                        149900, 41000, 131000, 20,
                        "SUV médio com motor turbo e muito espaço para a família. Garantia de fábrica até 2025 já cumprida com revisões em dia.",
                        false,
                        List.of(new Gasto(18, FUNILARIA_PINTURA, "Martelinho de ouro na porta traseira", 450),
                                new Gasto(16, ESTETICA, "Polimento técnico", 520)),
                        List.of(new Contato(TipoProposta.DUVIDA, "Fernanda Rocha", null, "fe.rocha@example.com",
                                "Ele tem as duas chaves e o manual?", null, null, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("HBZ1C09", "Branco Atlas",
                        ficha("Hyundai", "HB20", "Comfort 1.0", "2021", "2022", "Hatch", "1.0 Kappa, 3 cilindros",
                                "80 cv (etanol)", "10,2 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "300 L", "50 L", "13,1 km/l", "15,0 km/l",
                                "Ar-condicionado, direção elétrica, multimídia com Android Auto, câmera de ré"),
                        69900, 39100, 59000, 15,
                        "Primeiro carro ideal: econômico, revisado e com IPVA 2026 pago.",
                        false,
                        List.of(new Gasto(14, IPVA_LICENCIAMENTO, "IPVA 2026 quitado", 1980),
                                new Gasto(12, ESTETICA, "Lavagem completa", 120)),
                        List.of(new Contato(TipoProposta.FINANCIAMENTO, "Lucas Prado", "19996612233", null,
                                        "Sou autônomo, consigo financiar sem entrada?", null, null, StatusProposta.NOVA),
                                new Contato(TipoProposta.DUVIDA, "Bianca Teixeira", "19993321100", "bianca.t@example.com",
                                        "Ainda está disponível?", null, null, StatusProposta.DESCARTADA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("CRT5D33", "Azul Topázio",
                        ficha("Hyundai", "Creta", "Platinum 1.0 TGDI", "2023", "2024", "SUV", "1.0 turbo TGDI, 3 cilindros",
                                "120 cv (etanol)", "17,5 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "5",
                                "422 L", "50 L", "11,0 km/l", "13,2 km/l",
                                "Teto solar panorâmico, Bluelink, bancos ventilados, piloto adaptativo, alerta de ponto cego"),
                        139900, 22300, 124000, 10,
                        "Seminovo com cara de zero: 22 mil km, ainda na garantia de fábrica. Teto panorâmico e pacote completo de segurança.",
                        true,
                        List.of(new Gasto(9, DOCUMENTACAO, "Transferência", 610)),
                        List.of(new Contato(TipoProposta.TEST_DRIVE, "Thiago Barbosa", "19998741122", "thiago.b@example.com",
                                "Quero levar minha esposa para ver no sábado.", null, 3, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("KWD8E12", "Laranja Ocre",
                        ficha("Renault", "Kwid", "Zen 1.0", "2022", "2023", "Hatch", "1.0 SCe, 3 cilindros",
                                "71 cv (etanol)", "10,0 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "290 L", "38 L", "14,9 km/l", "15,6 km/l",
                                "Ar-condicionado, direção elétrica, 4 airbags, multimídia Media Evolution"),
                        52900, 28400, 44500, 60,
                        "O mais econômico da loja. Ótimo para aplicativo ou para o dia a dia na cidade.",
                        false,
                        List.of(new Gasto(58, MECANICA, "Troca de óleo e filtros", 380),
                                new Gasto(55, FUNILARIA_PINTURA, "Pintura do para-choque dianteiro", 750),
                                new Gasto(20, ANUNCIOS, "Impulsionamento do anúncio", 150)),
                        List.of(),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("ARG3G56", "Vermelho Montecarlo",
                        ficha("Fiat", "Argo", "Drive 1.0", "2021", "2022", "Hatch", "1.0 Firefly, 3 cilindros",
                                "77 cv (etanol)", "10,9 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "300 L", "48 L", "13,0 km/l", "14,6 km/l",
                                "Multimídia 7\", volante multifuncional, sensor de ré, rodas de liga leve"),
                        64900, 47300, 55000, 33,
                        "Hatch espaçoso, revisado, com multimídia e sensor de ré.",
                        false,
                        List.of(new Gasto(30, PNEUS, "Dois pneus dianteiros novos", 860)),
                        List.of(new Contato(TipoProposta.PROPOSTA, "Gustavo Henrique", "19991112233", null,
                                "Fecho por 62 mil com sinal hoje.", 62000, null, StatusProposta.EM_ATENDIMENTO)),
                        StatusAnuncio.RESERVADO, null),

                new CarroDemo("TCR6H90", "Branco Puro",
                        ficha("Volkswagen", "T-Cross", "Comfortline 200 TSI", "2021", "2022", "SUV", "1.0 TSI turbo, 3 cilindros",
                                "128 cv (etanol)", "20,4 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "5",
                                "373 L", "52 L", "11,3 km/l", "13,8 km/l",
                                "Active Info Display, VW Play, sensor de estacionamento, controle de estabilidade"),
                        119900, 48000, 104000, 28,
                        "SUV compacto com painel digital e motor TSI. Único dono, todas as notas de manutenção.",
                        false,
                        List.of(new Gasto(26, MECANICA, "Troca de pastilhas e discos", 980),
                                new Gasto(24, ESTETICA, "Higienização do ar-condicionado", 180)),
                        List.of(new Contato(TipoProposta.DUVIDA, "Juliana Mendes", "19982223344", "ju.mendes@example.com",
                                "Aceita meu Onix 2019 na troca?", null, null, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("HLX9J14", "Prata Supernova",
                        ficha("Toyota", "Hilux", "SRV 2.8 4x4 Diesel", "2020", "2021", "Picape cabine dupla", "2.8 turbodiesel, 4 cilindros",
                                "204 cv", "50,9 kgfm", "Diesel", "Automático de 6 marchas", "4x4 com reduzida", "4", "5",
                                "1.000 kg (caçamba)", "80 L", "9,1 km/l", "11,0 km/l",
                                "Controle de descida, câmera de ré, bancos em couro, multimídia, capota marítima e santo antônio"),
                        239900, 89000, 212000, 50,
                        "A picape mais desejada do mercado. Revisões na Toyota, nunca usada em fazenda. Pronta para trabalho ou viagem.",
                        true,
                        List.of(new Gasto(48, MECANICA, "Revisão dos 90 mil km", 2350),
                                new Gasto(46, PNEUS, "Jogo de pneus AT 265/65 R17", 4200),
                                new Gasto(45, DOCUMENTACAO, "Transferência e vistoria", 690),
                                new Gasto(10, ANUNCIOS, "Anúncio em portal especializado", 390)),
                        List.of(new Contato(TipoProposta.FINANCIAMENTO, "Marcos Vinícius Duarte", "16997775566", "marcos.duarte@example.com",
                                        "Sou produtor rural, tenho crédito pré-aprovado no banco.", null, null, StatusProposta.EM_ATENDIMENTO),
                                new Contato(TipoProposta.TEST_DRIVE, "Sérgio Almeida", "19994445566", null,
                                        null, null, 5, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("TRK4K28", "Preto Ouro Negro",
                        ficha("Chevrolet", "Tracker", "Premier 1.2 Turbo", "2022", "2023", "SUV", "1.2 turbo, 3 cilindros",
                                "133 cv (etanol)", "21,4 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "5",
                                "393 L", "44 L", "11,1 km/l", "13,1 km/l",
                                "Teto solar, Wi-Fi nativo, OnStar, estacionamento automático, alerta de colisão"),
                        129900, 30100, 113000, 18,
                        "Versão topo com teto solar e estacionamento automático. Baixa quilometragem.",
                        false,
                        List.of(new Gasto(15, ESTETICA, "Polimento e cristalização", 480)),
                        List.of(),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("KCK7L63", "Cinza Grafite",
                        ficha("Nissan", "Kicks", "SV 1.6", "2021", "2021", "SUV", "1.6 16V, 4 cilindros",
                                "114 cv (etanol)", "15,5 kgfm", "Flex", "CVT", "Dianteira", "4", "5",
                                "432 L", "41 L", "11,4 km/l", "13,7 km/l",
                                "Multimídia com espelhamento, câmera de ré, controle de tração, rodas aro 17"),
                        94900, 55200, 82000, 70,
                        "SUV confortável e econômico. Preço abaixo da tabela para venda rápida.",
                        false,
                        List.of(new Gasto(68, MECANICA, "Troca do fluido do CVT", 890),
                                new Gasto(66, FUNILARIA_PINTURA, "Reparo de risco no para-lama", 620),
                                new Gasto(35, ANUNCIOS, "Impulsionamento do anúncio", 150),
                                new Gasto(5, OPERACIONAL, "Estacionamento extra no pátio", 200)),
                        List.of(new Contato(TipoProposta.PROPOSTA, "Aline Cardoso", "19983334455", "aline.c@example.com",
                                "Faço 88 mil à vista.", 88000, null, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("TOR2M71", "Vermelho Alpine",
                        ficha("Fiat", "Toro", "Volcano 2.0 4x4 Diesel", "2021", "2022", "Picape cabine dupla", "2.0 turbodiesel, 4 cilindros",
                                "170 cv", "35,7 kgfm", "Diesel", "Automático de 9 marchas", "4x4", "4", "5",
                                "937 L (caçamba)", "60 L", "10,1 km/l", "12,3 km/l",
                                "Multimídia 10\", bancos em couro, ar digital dual zone, câmera de ré, partida remota"),
                        159900, 67000, 139000, 25,
                        "Picape diesel 4x4 com câmbio de 9 marchas. Capota marítima e protetor de caçamba.",
                        false,
                        List.of(new Gasto(22, MECANICA, "Troca de correia e revisão", 1650),
                                new Gasto(21, DOCUMENTACAO, "Transferência", 650)),
                        List.of(new Contato(TipoProposta.FINANCIAMENTO, "Roberto Nogueira", "19995556677", null,
                                "Pode reservar? Estou aguardando aprovação do banco.", null, null, StatusProposta.EM_ATENDIMENTO)),
                        StatusAnuncio.RESERVADO, null),

                new CarroDemo("GOL5N36", "Branco Cristal",
                        ficha("Volkswagen", "Gol", "1.0 MPI", "2019", "2020", "Hatch", "1.0 MPI, 3 cilindros",
                                "84 cv (etanol)", "10,4 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "285 L", "55 L", "12,8 km/l", "14,1 km/l",
                                "Ar-condicionado, direção hidráulica, travas e vidros elétricos"),
                        49900, 72000, 41000, 9,
                        "Em preparação: aguardando funilaria antes de ir para a vitrine.",
                        false,
                        List.of(new Gasto(7, FUNILARIA_PINTURA, "Orçamento de funilaria (sinal)", 400),
                                new Gasto(6, MECANICA, "Embreagem nova", 1250)),
                        List.of(),
                        StatusAnuncio.OCULTO, null),

                new CarroDemo("HRV3P48", "Azul Cosmic",
                        ficha("Honda", "HR-V", "EXL 1.5", "2022", "2023", "SUV", "1.5 i-VTEC, 4 cilindros",
                                "126 cv (etanol)", "15,8 kgfm", "Flex", "CVT", "Dianteira", "4", "5",
                                "354 L", "50 L", "11,6 km/l", "13,5 km/l",
                                "Bancos em couro, Honda Sensing, multimídia 8\", câmera multivisão, ar digital"),
                        149900, 29000, 132000, 12,
                        "Nova geração, completo e com garantia de fábrica. Revisões só na Honda.",
                        false,
                        List.of(new Gasto(11, ESTETICA, "Lavagem técnica de motor e interior", 250)),
                        List.of(new Contato(TipoProposta.DUVIDA, "Camila Ribeiro", "19987776655", "camila.ribeiro@example.com",
                                "Qual o valor da parcela em 60x?", null, null, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("BYD1Q52", "Verde Surf",
                        ficha("BYD", "Dolphin", "EV 95 cv", "2023", "2024", "Hatch", "Motor elétrico síncrono",
                                "95 cv", "18,4 kgfm", "Elétrico", "Automático (redução única)", "Dianteira", "4", "5",
                                "345 L", "Bateria 44,9 kWh", "Autonomia 291 km (Inmetro)", "Autonomia 291 km (Inmetro)",
                                "Tela giratória 12,8\", carregador por indução, câmera 360°, frenagem autônoma, carregador de parede incluso"),
                        129900, 15200, 112000, 8,
                        "100% elétrico, gasto de energia de cerca de R$ 0,15 por km. Acompanha carregador de parede.",
                        true,
                        List.of(new Gasto(6, DOCUMENTACAO, "Transferência e vistoria", 590)),
                        List.of(new Contato(TipoProposta.TEST_DRIVE, "Henrique Sato", "11991238877", "h.sato@example.com",
                                        "Nunca dirigi elétrico, quero testar.", null, 1, StatusProposta.NOVA),
                                new Contato(TipoProposta.DUVIDA, "Larissa Fontes", null, "larissa.fontes@example.com",
                                        "Quanto tempo leva para carregar na tomada comum?", null, null, StatusProposta.EM_ATENDIMENTO)),
                        StatusAnuncio.DISPONIVEL, null),

                new CarroDemo("SPN6C42", "Prata Switchblade",
                        mais(ficha("Chevrolet", "Spin", "Premier 1.8 7 lugares", "2022", "2023", "Minivan", "1.8 SPE/4, 4 cilindros",
                                        "111 cv (etanol)", "17,7 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "7",
                                        "553 L", "53 L", "8,9 km/l", "10,6 km/l",
                                        "Terceira fileira de bancos, MyLink com CarPlay, câmera de ré, rack de teto, ar-condicionado"),
                                PORTA_MALAS_7_LUGARES, "162 L"),
                        104900, 34000, 89000, 22,
                        "Sete lugares de verdade para a família toda. Com a terceira fileira rebatida, sobra porta-malas de sobra para viagem.",
                        true,
                        List.of(new Gasto(20, MECANICA, "Revisão dos 30 mil km", 980),
                                new Gasto(18, ESTETICA, "Higienização dos 7 bancos", 380)),
                        List.of(new Contato(TipoProposta.TEST_DRIVE, "Família Moraes", "19993215566", "moraes.familia@example.com",
                                "Somos 6 em casa, queremos ver a terceira fileira.", null, 4, StatusProposta.NOVA)),
                        StatusAnuncio.DISPONIVEL, null),

                // ---------- já vendidos (alimentam o gráfico de vendas e o lucro) ----------
                new CarroDemo("PGT6R19", "Branco Banquise",
                        ficha("Peugeot", "208", "Allure 1.0", "2022", "2023", "Hatch", "1.0 Firefly, 3 cilindros",
                                "75 cv (etanol)", "10,7 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "265 L", "47 L", "13,4 km/l", "15,1 km/l",
                                "i-Cockpit, multimídia 10\", câmera de ré, faróis de LED"),
                        74900, 31000, 63000, 140,
                        "Hatch moderno com painel i-Cockpit.", false,
                        List.of(new Gasto(138, ESTETICA, "Polimento", 380), new Gasto(135, DOCUMENTACAO, "Transferência", 600)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Ana Paula Moreira", cpf("348120957"), 72500, 120, FormaPagamento.FINANCIAMENTO, false)),

                new CarroDemo("SDZ4S77", "Prata Switchblade",
                        ficha("Chevrolet", "S10", "LTZ 2.8 4x4 Diesel", "2019", "2020", "Picape cabine dupla", "2.8 turbodiesel, 4 cilindros",
                                "200 cv", "51 kgfm", "Diesel", "Automático de 6 marchas", "4x4", "4", "5",
                                "1.061 L (caçamba)", "76 L", "8,9 km/l", "10,8 km/l",
                                "MyLink, bancos em couro, controle de descida, câmera de ré"),
                        179900, 98000, 154000, 175,
                        "Picape robusta, revisada.", false,
                        List.of(new Gasto(170, MECANICA, "Revisão completa e bicos injetores", 2800),
                                new Gasto(165, PNEUS, "Jogo de pneus", 3600),
                                new Gasto(160, COMISSAO, "Comissão do vendedor", 1500)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Agropecuária Boa Vista Ltda", cnpj("123456780001"), 175000, 150, FormaPagamento.A_VISTA, false)),

                new CarroDemo("DST8T05", "Marrom Vison",
                        ficha("Renault", "Duster", "Iconic 1.3 Turbo", "2021", "2022", "SUV", "1.3 TCe turbo, 4 cilindros",
                                "170 cv (etanol)", "27,5 kgfm", "Flex", "CVT", "Dianteira", "4", "5",
                                "475 L", "50 L", "10,5 km/l", "12,6 km/l",
                                "Multimídia Easy Link, câmera multiview, ar digital, partida por botão"),
                        99900, 46000, 84000, 115,
                        "SUV espaçoso com motor turbo.", false,
                        List.of(new Gasto(112, MECANICA, "Troca de óleo e filtros", 420), new Gasto(110, FUNILARIA_PINTURA, "Retoque na porta", 650)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Diego Santana", cpf("572903418"), 97000, 95, FormaPagamento.TROCA_COM_TROCO, false)),

                new CarroDemo("RGR2U83", "Azul Belize",
                        ficha("Ford", "Ranger", "XLS 2.2 4x4 Diesel", "2019", "2019", "Picape cabine dupla", "2.2 turbodiesel, 4 cilindros",
                                "160 cv", "39,3 kgfm", "Diesel", "Automático de 6 marchas", "4x4", "4", "5",
                                "1.180 L (caçamba)", "80 L", "9,3 km/l", "11,2 km/l",
                                "SYNC 3, sensor de estacionamento, controle de tração, engate"),
                        159900, 104000, 136000, 75,
                        "Picape de trabalho, mecânica revisada.", false,
                        List.of(new Gasto(72, MECANICA, "Revisão e troca de amortecedores", 2100), new Gasto(70, DOCUMENTACAO, "Transferência", 650)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Paulo Roberto Lima", cpf("615027384"), 155000, 55, FormaPagamento.FINANCIAMENTO, false)),

                new CarroDemo("YRS5V31", "Vermelho Granada",
                        ficha("Toyota", "Yaris", "XS 1.5", "2021", "2022", "Hatch", "1.5 Dual VVT-i, 4 cilindros",
                                "110 cv (etanol)", "14,9 kgfm", "Flex", "CVT", "Dianteira", "4", "5",
                                "310 L", "45 L", "12,4 km/l", "14,3 km/l",
                                "Multimídia com CarPlay, 7 airbags, controle de estabilidade"),
                        88900, 36000, 75000, 40,
                        "Hatch Toyota, muito econômico.", false,
                        List.of(new Gasto(38, ESTETICA, "Higienização", 220)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Renata Figueiredo", cpf("230984751"), 86000, 28, FormaPagamento.PIX, false)),

                new CarroDemo("NVS7W64", "Cinza Moonstone",
                        ficha("Volkswagen", "Nivus", "Highline 200 TSI", "2022", "2023", "SUV cupê", "1.0 TSI turbo, 3 cilindros",
                                "128 cv (etanol)", "20,4 kgfm", "Flex", "Automático de 6 marchas", "Dianteira", "4", "5",
                                "415 L", "52 L", "11,7 km/l", "14,0 km/l",
                                "VW Play, ACC, frenagem autônoma, painel digital, faróis full LED"),
                        124900, 27000, 106000, 30,
                        "SUV cupê completo, baixa quilometragem.", false,
                        List.of(new Gasto(28, DOCUMENTACAO, "Transferência", 620), new Gasto(20, ANUNCIOS, "Fotos profissionais", 280)),
                        List.of(new Contato(TipoProposta.PROPOSTA, "Vanessa Albuquerque", "19984561234", "vanessa.alb@example.com",
                                        "Pago 122 mil, metade PIX e metade financiado.", 122000, null, StatusProposta.NOVA),
                                new Contato(TipoProposta.DUVIDA, "Otávio Ramos", "19985557788", null,
                                        "Tem garantia?", null, null, StatusProposta.NOVA)),
                        StatusAnuncio.VENDIDO,
                        new VendaDemo("Vanessa Albuquerque", cpf("409318265"), 122000, 10, FormaPagamento.FINANCIAMENTO, true)),

                new CarroDemo("MOB3X97", "Branco Alaska",
                        ficha("Fiat", "Mobi", "Like 1.0", "2022", "2023", "Hatch", "1.0 Fire, 4 cilindros",
                                "75 cv (etanol)", "9,9 kgfm", "Flex", "Manual de 5 marchas", "Dianteira", "4", "5",
                                "235 L", "47 L", "13,5 km/l", "15,0 km/l",
                                "Ar-condicionado, direção elétrica, multimídia"),
                        54900, 22000, 46000, 25,
                        "Compacto, ótimo para a cidade.", false,
                        List.of(new Gasto(23, ESTETICA, "Lavagem completa", 120), new Gasto(22, COMISSAO, "Comissão do vendedor", 500)),
                        List.of(), StatusAnuncio.VENDIDO,
                        new VendaDemo("Igor Batista", cpf("781256093"), 53500, 3, FormaPagamento.A_VISTA, false)));
    }

    /** Todas as placas fictícias da demonstração (as 3 da base original, o catálogo e as fichas pendentes). */
    public static Set<String> placas() {
        Set<String> placas = new HashSet<>(List.of("ABC1D23", "BRA2E19", "XYZ9876"));
        carros().forEach(c -> placas.add(c.placa()));
        fichasPendentes().forEach(f -> placas.add(f.placa()));
        return placas;
    }

    static List<FichaPendente> fichasPendentes() {
        return List.of(
                new FichaPendente("CVC9Y21", valores(MARCA, "Chevrolet", MODELO, "Cruze", VERSAO, "LTZ 1.4 Turbo",
                        ANO_MODELO, "2021", COR, "Branco Summit", COMBUSTIVEL, "Flex")),
                new FichaPendente("SAV4Z50", valores(MARCA, "Mitsubishi", MODELO, "Pajero Sport", ANO_FABRICACAO, "2020",
                        COR, "Preto Ônix")));
    }

    private static Map<CampoTecnico, String> ficha(String... valores) {
        Map<CampoTecnico, String> mapa = new EnumMap<>(CampoTecnico.class);
        for (int i = 0; i < valores.length; i++) {
            mapa.put(ORDEM_FICHA.get(i), valores[i]);
        }
        return mapa;
    }

    private static Map<CampoTecnico, String> mais(Map<CampoTecnico, String> ficha, CampoTecnico campo, String valor) {
        ficha.put(campo, valor);
        return ficha;
    }

    private static Map<CampoTecnico, String> valores(Object... pares) {
        Map<CampoTecnico, String> mapa = new EnumMap<>(CampoTecnico.class);
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((CampoTecnico) pares[i], (String) pares[i + 1]);
        }
        return mapa;
    }

    /** CPF fictício com dígitos verificadores válidos, a partir dos 9 primeiros dígitos. */
    static String cpf(String base) {
        return comDigitos(base, 2);
    }

    /** CNPJ fictício com dígitos verificadores válidos, a partir dos 12 primeiros dígitos. */
    static String cnpj(String base) {
        String s = base;
        for (int n = 0; n < 2; n++) {
            int soma = 0;
            for (int i = s.length() - 1, peso = 2; i >= 0; i--, peso = peso == 9 ? 2 : peso + 1) {
                soma += (s.charAt(i) - '0') * peso;
            }
            int resto = soma % 11;
            s += resto < 2 ? 0 : 11 - resto;
        }
        return s;
    }

    private static String comDigitos(String base, int quantos) {
        String s = base;
        for (int n = 0; n < quantos; n++) {
            int soma = 0;
            for (int i = 0; i < s.length(); i++) {
                soma += (s.charAt(i) - '0') * (s.length() + 1 - i);
            }
            int resto = (soma * 10) % 11;
            s += resto == 10 ? 0 : resto;
        }
        return s;
    }
}
