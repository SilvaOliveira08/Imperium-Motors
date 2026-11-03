# Imperium Motors

Plataforma web para **organizar e consolidar planilhas**, **montar fichas técnicas de veículos** (indicando de onde veio cada informação) e **vender os carros num site de loja**, com controle de custos, interessados e vendas. Projeto acadêmico de Programação Orientada a Objetos (Java + Spring Boot).

A documentação de arquitetura e as decisões técnicas estão em [`docs/SDD.md`](docs/SDD.md); o backlog das sprints está em [`docs/ISSUES.md`](docs/ISSUES.md).

## O que o MVP faz

**Planilhas**
- Importa arquivos XLSX e CSV (detecta separador `;`, `,` ou tabulação e codificação UTF-8 ou ISO-8859-1).
- Mostra a pré-visualização com paginação e um relatório de qualidade (duplicatas e células vazias por coluna).
- Seleciona, reordena e renomeia colunas.
- Padroniza valores: CPF, CNPJ, placa, telefone, data, maiúsculas, nome próprio, remover acentos e espaços.
- Remove duplicatas (todas as colunas ou por uma coluna-chave) e linhas sem valor numa coluna.
- Combina planilhas por uma chave (CPF, placa, código), com junção interna, à esquerda ou completa. Pontos, traços e espaços são ignorados na comparação.
- Desfaz etapas e exporta o resultado em XLSX ou CSV.

**Fichas técnicas**
- Cria a ficha a partir da placa e, opcionalmente, de um documento PDF (com texto) ou TXT.
- Lê linhas como `Motor: 1.0 TSI` e os campos combinados do CRLV (`MARCA/MODELO/VERSÃO`, `ANO FAB/MOD`).
- Consulta as fontes na ordem: documento, API de placa (se configurada) e base de demonstração.
- Cada campo guarda a **origem** (usuário, documento, fonte externa) e o **status** (confirmado, a confirmar, ausente). Nada vindo de documento ou fonte externa aparece como confirmado sem revisão.
- A ficha só é concluída quando os campos obrigatórios estão confirmados e não há nada pendente.
- Gera a ficha em PDF; enquanto não concluída, o PDF sai marcado como rascunho.

**Loja de veículos (site de vendas)**
- Site público em `/`: catálogo com busca, filtros (marca, câmbio, combustível, preço, ano, km) e ordenação, layout para celular.
- Página do carro com galeria de fotos, ficha técnica (só campos confirmados; placa e chassi não aparecem), PDF da ficha, simulação de financiamento (Tabela Price) e botão de WhatsApp.
- Formulário de contato: dúvida, test drive (com data), proposta (com valor) ou financiamento. Vai para a tela **Interessados**.
- **Só uma ficha técnica concluída vira anúncio.** Se a ficha voltar para revisão, o carro sai da loja sozinho.
- Estoque: preço, km, fotos (até 8, JPG/PNG/WEBP conferidos pela assinatura), destaque e situação (disponível, reservado, fora da loja, vendido).
- Custos por carro: valor de compra + despesas por categoria (mecânica, funilaria, documentação, pneus…), com custo total, lucro e margem prevista ou realizada.
- **Importa despesas de planilha** (XLSX/CSV com Data, Placa, Categoria, Descrição, Valor). A planilha `excel/Controle_Despesas_Veiculos.xlsx` é lida direto pela aba "Despesas"; cada linha é ligada ao carro pela placa e guarda a origem ("Planilha: arquivo, linha N").
- Registro de venda (comprador, CPF/CNPJ, forma de pagamento) a partir de um interessado ou não; desfazer venda.
- Painel com indicadores (estoque, valor e custo, vendas e lucro do mês, ticket médio, dias até vender, contatos novos), gráfico dos últimos 6 meses e relatórios XLSX de estoque, despesas e vendas.
- Área da equipe protegida por login (Spring Security, CSRF, cabeçalho CSP). A loja é pública.

**Geral:** histórico de operações, validação de tipo, tamanho e conteúdo dos arquivos, mensagens de erro claras e layout responsivo.

## Como executar

Pré-requisitos: **JDK 21**. Para usar PostgreSQL, também **Docker**.

### Opção 1: com PostgreSQL (padrão)

```bash
docker compose up -d banco                  # sobe o PostgreSQL 16
export DATAFLOW_ADMIN_SENHA='uma-senha-forte'  # Windows (PowerShell): $env:DATAFLOW_ADMIN_SENHA='uma-senha-forte'
./mvnw spring-boot:run                      # Windows: mvnw.cmd spring-boot:run
```

### Opção 2: sem instalar banco (H2 em arquivo)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Opção 3: tudo em containers

```bash
DATAFLOW_ADMIN_SENHA='uma-senha-forte' docker compose --profile app up --build
```

Depois abra <http://localhost:8080>: a loja é a página inicial. A área da equipe fica em <http://localhost:8080/painel> (usuário `admin`; no perfil local a senha de desenvolvimento é `dataflow123`; nas opções 1 e 3 é a que você definiu em `DATAFLOW_ADMIN_SENHA`, com pelo menos 10 caracteres. Sem ela, ou com a senha de desenvolvimento, o sistema não inicia fora do perfil local). No painel, o botão **Carregar carros de demonstração** monta uma loja de exemplo com 3 carros, despesas, interessados e uma venda.

As tabelas são criadas automaticamente pelo Flyway (`src/main/resources/db/migration`). Os arquivos enviados ficam em `./dados/arquivos` (fora da pasta pública).

### Testes

```bash
./mvnw test
```

São 116 testes: unidade das regras de negócio (regras de transformação, consolidação, leitores e escritores, ficha técnica, interpretação de documentos, anúncio, venda, rentabilidade, financiamento, filtros da loja, interessados) e integração dos fluxos completos pela camada web com H2 em memória, incluindo login, CSRF e o fluxo ficha → anúncio → loja → contato → despesas (manual e por planilha) → venda → relatórios.

Por padrão os testes usam H2 em memória (modo PostgreSQL), que é rápido e não precisa de nada instalado. Para rodar os mesmos testes contra um PostgreSQL 16 de verdade, num container criado pelo Testcontainers (precisa de Docker):

```bash
./mvnw verify -Ppostgres
```

No GitHub, o CI (`.github/workflows/ci.yml`) roda `./mvnw -B verify` com H2 e com `-Ppostgres` a cada push na branch principal e em todo pull request, e depois monta a imagem Docker. O Dependabot (`.github/dependabot.yml`) abre toda semana pull requests com atualizações do Maven, das Actions e da imagem base.

## Roteiro de demonstração

Os arquivos de `exemplos/` têm dados fictícios.

1. **Planilhas → Importar** `clientes.csv` e `veiculos.xlsx`.
2. Abra `clientes.csv`: o relatório aponta duplicatas e campos vazios.
3. **Organizar**: padronize *CPF* como CPF, *Nome* como Nome Próprio, *Telefone* e *Data de cadastro*; marque *Remover linhas duplicadas* comparando por *CPF*; desmarque *Cidade*; renomeie *Nome* para *Cliente*. Aplicar.
4. **Combinar** com `veiculos.xlsx` usando *CPF* = *CPF do proprietário*, junção "Todos da primeira".
5. **Exportar XLSX**.
6. **Fichas técnicas → Nova ficha**: placa `ABC1D23` e documento `documento-veiculo.pdf`. Veja quais campos vieram do documento e quais da base de demonstração.
7. Tente **Salvar e concluir**: o sistema lista as pendências. Use **Marcar todos os "a confirmar"**, conclua e gere o PDF.

8. **Estoque → Anunciar carro**: escolha a ficha concluída, informe preço e km, envie fotos. Abra a **loja** (`/`) e veja o carro.
9. Na página do carro, simule o financiamento e envie uma proposta. Ela aparece em **Interessados**.
10. No carro, lance despesas ou importe `excel/Controle_Despesas_Veiculos_EXEMPLO.xlsx` em **Estoque → Importar despesas** (as placas precisam estar no estoque).
11. **Registrar venda** escolhendo o interessado. O carro sai da loja e o painel mostra faturamento e lucro.

Placas da base de demonstração: `ABC1D23`, `BRA2E19`, `XYZ9876`. Também dá para testar só com documento usando `ficha-fabricante.txt` e qualquer placa válida.

## Configuração

| Propriedade / variável | Padrão | Uso |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/dataflow`, `dataflow`, `dataflow` | Conexão com o banco |
| `DATAFLOW_ARQUIVOS` | `./dados/arquivos` | Onde os arquivos enviados são guardados |
| `dataflow.armazenamento.tamanho-maximo-mb` | `10` | Tamanho máximo por arquivo |
| `dataflow.fichas.fonte-demonstracao` | `true` | Liga a base de dados fictícios |
| `API_PLACA_URL`, `API_PLACA_TOKEN` | vazio | API de consulta veicular autorizada; desligada sem URL |
| `DATAFLOW_ADMIN_USUARIO`, `DATAFLOW_ADMIN_SENHA` | `admin`, sem senha (obrigatória; `dataflow123` só no perfil local) | Login da equipe no painel |
| `dataflow.seguranca.login.maximo-falhas`, `dataflow.seguranca.login.janela-minutos` | `5`, `15` | Senhas erradas seguidas (mesmo IP e usuário) até bloquear o login, e por quanto tempo |
| `DATAFLOW_FOTOS` | `./dados/fotos` | Onde ficam as fotos dos anúncios |
| `DATAFLOW_TOMCAT_DIR` | `./dados/tomcat` | Pasta de trabalho do servidor (arquivos temporários dos uploads); fica fora de `/tmp`, que o sistema limpa |
| `DATAFLOW_LOJA_NOME`, `DATAFLOW_LOJA_TELEFONE`, `DATAFLOW_LOJA_WHATSAPP`, `DATAFLOW_LOJA_ENDERECO` | dados fictícios | Contato exibido no site |
| `DATAFLOW_TAXA_FINANCIAMENTO` | `1.79` | Taxa mensal (%) usada na simulação |

## Estrutura

```
src/main/java/br/com/dataflowauto
├── comum        arquivos importados, histórico, tratamento de erros, login (SegurancaConfig), formatação
├── planilhas
│   ├── dominio  Planilha, Coluna, Linha, ConsolidadorPlanilhas, AnalisadorQualidade,
│   │            LeitorPlanilha/EscritorPlanilha (interfaces), regras/ (RegraTransformacao e subclasses)
│   ├── aplicacao PlanilhaService
│   ├── infra    LeitorXlsx, LeitorCsv, EscritorXlsx, EscritorCsv
│   └── web      PlanilhaController, AreaTrabalho (sessão), OrganizacaoForm
└── fichas
    ├── dominio  FichaTecnica, CampoFicha, Veiculo, Placa, CampoTecnico, FonteDadosVeiculo (interface),
    │            InterpretadorDocumento
    ├── aplicacao FichaService
    ├── infra    FonteDocumento, FonteApiPlaca, FonteDemonstracao, ExtratorTextoDocumento, GeradorFichaPdf
    └── web      FichaController, RevisaoForm
└── vendas
    ├── dominio  Anuncio, Venda (embutida), Despesa, Proposta, Rentabilidade, SimulacaoFinanciamento,
    │            FiltroLoja, Dinheiro, enums de status, categoria e forma de pagamento
    ├── aplicacao AnuncioService, DespesaService (importação de planilha), PropostaService, PainelService,
    │            DemonstracaoService, UsoFichaPelaLoja (impede excluir ficha anunciada)
    ├── infra    repositórios JPA, ArmazenamentoFotos
    └── web      LojaController (site público), AnuncioController, PropostaController, FotoController
```

## Tecnologias

Java 21, Spring Boot 3.4 (Web, Thymeleaf, Data JPA, Validation, Security), PostgreSQL 16 + Flyway, Apache POI, Apache Commons CSV, Apache PDFBox, OpenPDF, JUnit 5 e AssertJ.
