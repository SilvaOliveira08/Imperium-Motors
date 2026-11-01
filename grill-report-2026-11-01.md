---
plugin: grill
version: 1.2.3
date: 2026-11-01
target: dataflow-auto (Java 21 + Spring Boot 3.4.1)
style: Select All (Paranoid Mode + Crítica e Roteiro + Painel de Especialistas + ADRs + Plano de Arquitetura)
addons: escala 100x, custos escondidos, violações de princípios, strangler fig, métricas de sucesso, antes vs depois, auditoria de premissas, código para enxugar
agents: recon, architecture, error-handling, security, testing, edge-cases
---

# Pente-fino do DataFlow Auto — 01/11/2026

## Como ler este relatório

- **Severidade**: `[CRÍTICO]` resolver antes de publicar · `[ALTO]` resolver nesta sprint · `[MÉDIO]` próxima sprint · `[BAIXO]` quando mexer no arquivo · `[BOM]` o que está certo e deve ser mantido.
- **Esforço**: `<1 dia`, `<1 semana`, `<1 mês`.
- Caminhos de código são relativos a `src/main/java/br/com/dataflowauto/`, salvo indicação.
- **Verificado** = conferido diretamente no código por mim depois dos agentes. Os demais itens vêm da leitura dos agentes, com arquivo e linha.

### Escopo e limites

- 108 classes Java de produção (~8,3 mil linhas), 14 classes de teste, 21 templates, 5 migrações Flyway.
- **Testes executados no PC do usuário**: `mvnw test` → **89 testes, 0 falhas, BUILD SUCCESS** (o README fala em 81; está desatualizado).
- Não foi feito teste de carga, nem execução contra PostgreSQL real, nem varredura de CVEs com scanner. Afirmações sobre versões vulneráveis estão marcadas como "a confirmar".
- Um ajuste do relatório dos agentes: as FKs de `despesa`, `proposta`, `anuncio_foto` e `publicacao` **têm** `ON DELETE CASCADE` (V2, V5 — verificado). Então o cenário "excluir anúncio falha por FK e deixa fotos quebradas" não acontece; o risco real é o oposto: a exclusão apaga leads e despesas em cascata (item M6).

---

## Veredito em um parágrafo

O DataFlow Auto está **bem acima da média para um projeto acadêmico**: módulos separados por camada, domínio rico e encapsulado (`FichaTecnica`, `Anuncio`, `CampoFicha`), uso correto de polimorfismo (`RegraTransformacao` com Template Method, `TipoPadronizacao` como Strategy, cadeia de `FonteDadosVeiculo`), dinheiro em `BigDecimal`, `open-in-view=false`, Flyway com `ddl-auto=validate`, CSRF, CSP estrita, uploads conferidos pela assinatura e 89 testes passando. Os problemas que existem não são de "código ruim", e sim de **coisas que só aparecem com uso real**: importar a mesma planilha de despesas duas vezes duplica todos os custos sem aviso; o PDF público da ficha mostra a placa completa, contrariando a regra do próprio projeto; a senha padrão do painel está no README; e a chamada à API de placa não tem timeout e roda dentro de uma transação, o que pode derrubar a loja pública. **O maior risco é financeiro e silencioso** (despesas duplicadas), e o maior risco de reputação é o vazamento da placa no PDF.

---

## 1. Achados consolidados

### 1.1 Crítico

**C1 · Senha padrão do painel publicada e aceita em produção** `[CRÍTICO]` `<1 dia`
- **Onde**: `src/main/resources/application.properties:29-30`; `comum/web/SegurancaConfig.java:58-63`; `docker-compose.yml:23-27`; `README.md:63,103`.
- **O quê**: se `DATAFLOW_ADMIN_SENHA` não for definida, o login é `admin/dataflow123`, que está escrito no README. O sistema só registra um aviso no log. O serviço `app` do docker-compose nunca define essa variável.
- **Cenário**: alguém publica com `docker compose --profile app up`; qualquer pessoa entra em `/entrar` com `admin/dataflow123` e baixa CPF/CNPJ de compradores em `/painel/exportar/vendas`, todas as planilhas importadas e os leads.
- **Correção**: falhar na inicialização quando a senha for a padrão (ou tiver menos de 12 caracteres) fora do perfil `local`; tirar o valor padrão do `@Value`; no compose, `DATAFLOW_ADMIN_SENHA: ${DATAFLOW_ADMIN_SENHA:?defina a senha}`.
- **Observação**: para rodar localmente na aula, não é problema. Vira crítico no dia em que o site for para a internet.

### 1.2 Alto

**A1 · Reimportar a planilha de despesas duplica todos os custos** `[ALTO]` `<1 dia` — **verificado**
- **Onde**: `vendas/aplicacao/DespesaService.java:120-149`.
- **O quê**: cada linha vira uma `Despesa` nova com origem `"Planilha: <nome>, linha N"`, mas nada confere se essa linha já foi importada.
- **Cenário**: o fluxo descrito no README é manter uma planilha `Controle_Despesas_Veiculos.xlsx` que cresce todo mês e importá-la. Na segunda importação, todas as despesas antigas entram de novo. Custo total, lucro, margem, painel e relatórios ficam errados, e a mensagem ainda diz "N despesa(s) importada(s)".
- **Correção**: antes de salvar, pular linhas cuja chave (anúncio, data, categoria, valor, descrição normalizada) já existe, reportando "Linha N: já importada"; garantir com índice único. Opcional: guardar o hash do arquivo e recusar reenvio idêntico.

**A2 · PDF público da ficha mostra a placa completa e o nome do arquivo enviado** `[ALTO]` `<1 dia` — **verificado**
- **Onde**: `fichas/infra/GeradorFichaPdf.java:45` (título do documento), `:55` (subtítulo), `:75-77` (fontes consultadas); exposto em `GET /loja/carros/{id}/ficha-tecnica.pdf` (público).
- **O quê**: o modo `publico=true` só filtra a tabela de campos. A placa aparece no título e no subtítulo do PDF, e o rodapé lista o nome original do documento (ex.: `CRLV_Joao_Silva.pdf`). O README promete "placa e chassi não aparecem".
- **Cenário**: um robô baixa o PDF de todos os carros (IDs sequenciais) e coleta as placas, que servem para consultar débitos e dono, ou para clonagem. Nomes de arquivo podem vazar nome e CPF do proprietário.
- **Correção**: quando `publico`, usar título sem placa, trocar a linha por "Final da placa X" (como já faz a página HTML) e remover o rodapé de fontes. Adicionar teste que extrai o texto do PDF com PDFBox e confere que placa, chassi e nomes não aparecem (este teste ainda não existe: hoje o PDF público só tem a checagem de Content-Type, em `LojaIntegracaoTest.java:137-138`).

**A3 · API de placa sem timeout, chamada dentro de transação** `[ALTO]` `<1 dia` (timeout) / `<1 semana` (tirar da transação)
- **Onde**: `fichas/infra/FonteApiPlaca.java:42` (`RestClient.create()`, verificado); `fichas/aplicacao/FichaService.java:65-113`.
- **O quê**: o cliente HTTP não tem timeout de conexão nem de leitura, e a consulta acontece com uma conexão do banco já aberta.
- **Cenário**: a API do fornecedor trava. Umas 10 criações de ficha prendem as 10 conexões do pool, e a **loja pública** (`/`) para de responder junto.
- **Correção**: injetar `RestClient.Builder` com timeouts (3 s conexão, 5 s leitura); consultar as fontes antes de abrir a transação e só depois gravar. Isso também deixa a classe testável com `MockRestServiceServer`, o que hoje não é possível.

**A4 · Falha da API de placa é registrada como "nada encontrado"** `[ALTO]` `<1 dia`
- **Onde**: `fichas/infra/FonteApiPlaca.java:77-80`; `fichas/aplicacao/FichaService.java:174-178`; `fichas/web/FichaController.java:100-102`.
- **O quê**: qualquer erro (token vencido, 500, timeout) vira um mapa vazio. A ficha registra "API de consulta por placa (0 encontrados, 0 usados)" e a tela diz "nenhuma informação nova encontrada".
- **Cenário**: o token expira. O vendedor reconsulta multas e restrições, vê "nada novo" e informa isso ao comprador, mas a consulta nunca foi feita.
- **Correção**: distinguir "consultou e não achou" de "não conseguiu consultar"; registrar "API indisponível" na ficha e mostrar aviso.

**A5 · O mesmo carro pode ser anunciado e vendido duas vezes; sem controle de concorrência** `[ALTO]` `<1 dia` — **verificado**
- **Onde**: `vendas/aplicacao/AnuncioService.java:58` (checagem por ficha, não por veículo); `vendas/dominio/Anuncio.java` e `fichas/dominio/FichaTecnica.java` sem `@Version`.
- **O quê**: (a) a regra "um anúncio ativo" é verificada por ficha, mas uma placa pode ter várias fichas; (b) a verificação e a gravação não são atômicas, então um clique duplo cria dois anúncios; (c) sem `@Version`, duas pessoas vendendo o mesmo carro ao mesmo tempo sobrescrevem uma à outra, e a combinação "ocultar" + "vender" pode deixar o carro `OCULTO` com dados de venda.
- **Correção**: checar por veículo; índice único parcial no PostgreSQL (`... ON anuncio(ficha_id) WHERE status <> 'VENDIDO'`, idealmente com `veiculo_id`); `@Version` em `Anuncio`, `FichaTecnica` e `Proposta`, com mensagem amigável para `ObjectOptimisticLockingFailureException`.

**A6 · Catálogo público carrega o estoque inteiro 3 vezes por acesso** `[ALTO]` `<1 dia` (passo 1) / `<1 semana` (passo 2)
- **Onde**: `vendas/web/LojaController.java:78-83`; `vendas/aplicacao/AnuncioService.java:165-200`; associações `EAGER` em `Anuncio.java:46-48`; busca linear em `FichaTecnica.campo()`.
- **O quê**: `naLoja()`, `vitrine()` e `opcoesFiltro()` consultam o estoque cada um, e cada anúncio puxa ficha, veículo, ~41 campos e fotos. Filtros e ordenação são feitos em Java.
- **Cenário 100x**: com ~2.000 carros, cada visita à página inicial gera dezenas de milhares de consultas SQL. Um post que viraliza (o objetivo do módulo de marketing) derruba o site.
- **Correção**: (1) chamar `naLoja()` uma vez e repassar a lista; (2) trocar para `LAZY` com `@EntityGraph`/`JOIN FETCH` e índice por campo; (3) em escala real, filtrar e paginar no SQL.

**A7 · Área de trabalho de planilhas: memória sem limite e abas que se atropelam** `[ALTO]` `<1 semana`
- **Onde**: `planilhas/web/AreaTrabalho.java:18-43`; `planilhas/web/PlanilhaController.java:67-72, 97-122`; `planilhas/dominio/ConsolidadorPlanilhas.java:49-51`.
- **O quê**: cada sessão guarda até 21 cópias completas da planilha; combinar por uma chave repetida gera N×M linhas sem limite; a pré-visualização relê o arquivo a cada página. Duas abas no mesmo navegador compartilham a mesma área: editar na aba A aplica as regras na planilha aberta pela aba B.
- **Cenário**: dois usuários com CSVs de 10 MB derrubam a JVM, que também serve a loja. Ou: o usuário exporta a planilha errada sem perceber.
- **Correção**: limitar linhas por importação e o tamanho do resultado da junção (ex.: 200 mil linhas); cache da planilha lida por id; identificador da área de trabalho no formulário; guardar o desfazer como lista de regras reaplicadas (padrão Command), em vez de cópias.

**A8 · Mensagem de erro de upload esconde a causa real** `[ALTO]` `<1 dia`
- **Onde**: `comum/web/TratadorErros.java:28-32`.
- **O quê**: qualquer `MultipartException` (inclusive "pasta temporária sumiu") vira "passou do tamanho máximo (10 MB por arquivo)", sem log. Os limites reais são 5 MB por foto e 50 MB por envio.
- **Cenário**: o Linux limpa a pasta temporária do Tomcat após uns 10 dias; todos os uploads passam a falhar dizendo "arquivo grande demais", e o log não mostra nada.
- **Correção**: tratar só `MaxUploadSizeExceededException` como tamanho, com a mensagem montada a partir da configuração; logar os demais erros; fixar `server.tomcat.basedir` numa pasta persistente.

**A9 · Login sem proteção contra força bruta** `[ALTO]` `<1 dia`
- **Onde**: `comum/web/SegurancaConfig.java:38-41`.
- **O quê**: usuário conhecido (`admin`), tentativas ilimitadas, falhas não registradas.
- **Correção**: contador por IP + usuário (ex.: 5 falhas em 15 min), log das falhas com IP, usuário configurável.

**A10 · Sem CI, e o build do Docker pula os testes** `[ALTO]` `<1 dia`
- **Onde**: `.github/workflows/` não existe (issue #11 em `docs/ISSUES.md` ainda aberta); `Dockerfile:6` (`-DskipTests`).
- **Correção**: GitHub Actions com `./mvnw -B verify` em push e PR; job que constrói a imagem Docker; Dependabot para Maven; proteção de branch exigindo o build verde.

**A11 · Testes nunca rodam contra PostgreSQL** `[ALTO]` `<1 dia`
- **Onde**: `src/test/resources/application.properties:1` (H2 `MODE=PostgreSQL`); o perfil local usa `DEFAULT_NULL_ORDERING=HIGH` e o de teste não, então são três comportamentos diferentes.
- **Correção**: Testcontainers com `postgres:16` num perfil Maven usado no CI; manter H2 para rodar rápido localmente; alinhar a URL do H2 de teste.

### 1.3 Médio

| # | Achado | Onde | Correção resumida | Esforço |
|---|---|---|---|---|
| M1 | Dependência circular `comum ↔ vendas`, e serviço de aplicação dependendo da camada web | `comum/web/DadosLoja.java:56-57`; `marketing/aplicacao/DivulgacaoService.java:7,53` | Mover `DadosLoja` para `vendas` como `@ConfigurationProperties`; teste ArchUnit proibindo `comum → vendas/marketing` | <1 dia |
| M2 | Módulos acessam o `infra` uns dos outros | `vendas/aplicacao/DespesaService.java:13-14,104-106`; `DemonstracaoService.java:20`; `DivulgacaoService.java:16-17` | Expor só `aplicacao`/`dominio`; adicionar opções de leitura em `LeitorPlanilha` | <1 dia |
| M3 | Camada de aplicação depende de `MultipartFile` e de classes concretas de infra | `AnuncioService.java:24,56`; `PlanilhaService.java:21,61`; `FichaService.java:17-20` | Tipo `ArquivoEnviado` no domínio; interfaces `GeradorPdfFicha` e `ExtratorTexto` | <1 semana |
| M4 | `DivulgacaoService` com responsabilidades demais (SRP); compara enum por texto | `marketing/aplicacao/DivulgacaoService.java:49-243`, `:200` | Separar em kit, publicação, zip e demonstração; usar `== StatusAnuncio.OCULTO` | <1 semana |
| M5 | Desfazer venda deixa os leads fechados; status de proposta aceita qualquer transição | `AnuncioService.java:112-130`; `Proposta.java:122-128` | Venda lembra quais leads alterou e restaura; tabela de transições | <1 dia |
| M6 | Excluir anúncio apaga leads e despesas em cascata (verificado em V2/V5) | `db/migration/V2__loja_vendas.sql:34,46`; `AnuncioService.java:133-142` | Bloquear exclusão quando houver leads/despesas, ou exclusão lógica | <1 dia |
| M7 | Arquivos gravados/apagados fora do destino da transação | `AnuncioService.java:84-90,132-142,207-223`; `ArmazenamentoService.java:47-51` | Apagar em `afterCommit`; limpar o que foi gravado em rollback; validar todas as fotos antes de gravar | <1 dia |
| M8 | Valores do XLSX lidos como texto formatado (arredonda, perde sinal, CNPJ em notação científica) | `planilhas/infra/LeitorXlsx.java:119-132`; `vendas/dominio/Dinheiro.java:22` | Ler valor numérico bruto quando o chamador precisa de número; tratar `(1.234,56)` | <1 dia |
| M9 | Carro sem valor de compra conta custo zero e infla lucro e margem | `vendas/dominio/Rentabilidade.java:16`; `PainelService.java:59-65` | Lucro e margem nulos ("informe o valor de compra") e fora dos totais | <1 dia |
| M10 | Editar ficha concluída muda dados de carro publicado (ou vendido) sem nova revisão | `fichas/dominio/FichaTecnica.java:97-106,170-175` | Edição em ficha concluída volta para revisão; bloquear se o carro foi vendido | <1 dia |
| M11 | Leitor de CRLV grava lixo quando o rótulo vem sozinho na linha (MARCA = ":") e bloqueia o valor correto | `fichas/dominio/InterpretadorDocumento.java:17-18,44-55` | Exigir que o valor comece com letra ou número; testes para essa linha | <1 dia |
| M12 | Valor acima de 2.000 caracteres dá erro 500; TXT em ANSI perde acentos e campos | `fichas/dominio/CampoFicha.java:37`; `fichas/infra/ExtratorTextoDocumento.java:23` | Truncar ou rejeitar com mensagem; reaproveitar a detecção de codificação do `LeitorCsv` | <1 dia |
| M13 | Planilhas com CPF ficam guardadas para sempre, sem rota de exclusão (LGPD) | `comum/aplicacao/ArmazenamentoService.java:40-55`; `PlanilhaController` | Rota de exclusão e limpeza periódica configurável | <1 semana |
| M14 | Banco exposto na porta 5432 com `dataflow/dataflow` | `docker-compose.yml:4-9`; `application.properties:4-6` | Remover `ports:` ou ligar em `127.0.0.1`; senha obrigatória por variável | <1 dia |
| M15 | Leitura de XLSX/PDF inteira em memória, sem limite (arquivo-bomba) | `LeitorXlsx.java:51`; `LeitorCsv.java:42`; `ExtratorTextoDocumento.java:29` | Limites do POI (`ZipSecureFile`), teto de linhas/colunas, PDFBox com memória limitada | <1 semana |
| M16 | Formulário público de contato sem limite de envio | `vendas/web/LojaController.java:120-140` | Campo isca (honeypot), tempo mínimo de envio e limite por IP | <1 dia |
| M17 | Spring Boot 3.4.1 e POI 5.3.0 atrás de correções de segurança (**a confirmar com scanner**) | `pom.xml:10,22-25` | Atualizar para o 3.4.x/3.5.x mais recente e POI 5.4+; rodar os testes | <1 dia |
| M18 | Sem tratador genérico de erros nem código de erro; página pública manda "verificar o log" | `comum/web/TratadorErros.java`; `templates/error.html:9` | `@ExceptionHandler(Exception.class)` com ID curto no log e na tela; 400 para tipo inválido | <1 dia |
| M19 | Sem health check e métricas; quase nada é logado | `pom.xml` (sem Actuator); `docker-compose.yml:17-31` | Actuator com `/actuator/health` público e o resto protegido; healthcheck no compose; ID de requisição no log | <1 dia |
| M20 | Erro de disco vira "404 não encontrado", sem log | `vendas/infra/ArmazenamentoFotos.java:58-62`; `ArmazenamentoService.java:84-89` | Só `NoSuchFileException` vira 404; o resto vira 500 com log | <1 dia |
| M21 | Importação em lote desfaz tudo, mas a mensagem sugere sucesso parcial | `planilhas/aplicacao/PlanilhaService.java:60-82` | Validar tudo antes, ou importar arquivo por arquivo com resultado individual | <1 dia |
| M22 | Log da API de placa pode conter placa e RENAVAM; token enviado mesmo em `http://` | `FonteApiPlaca.java:63-79` | Logar só tipo e status; exigir `https` quando houver token | <1 dia |
| M23 | Testes de integração compartilham o mesmo banco e dependem da ordem | `src/test/.../vendas/LojaIntegracaoTest.java:126` | Limpar as tabelas depois de cada teste | <1 dia |
| M24 | Teste de "path traversal" passa por causa do Spring, não da validação do app | `LojaIntegracaoTest.java:253` | Testar nomes inválidos que chegam ao `ArmazenamentoFotos` (esperar 404) | <1 dia |
| M25 | Verificações negativas presas ao HTML exato nunca falham | `LojaIntegracaoTest.java:219,223,239` | Conferir o modelo ou o texto visível (jsoup) | <1 dia |
| M26 | Cobertura de 70% do domínio prometida e não medida | `docs/ISSUES.md:105`; `pom.xml` | JaCoCo com regra de 70% nos pacotes `dominio` | <1 dia |
| M27 | `AnuncioService` mistura leitura pública com comandos de admin | `AnuncioService.java:55-191`; `AnuncioController.java:136-189` | Extrair `CatalogoLojaService`; `DespesaController` e `VendaForm` | <1 semana |

### 1.4 Baixo (agrupado por arquivo)

- **`comum/web/TratadorErros.java:43-57`**: redirecionamento a partir do Referer aceita caminho `//host` do mesmo domínio; validar `^/(?![/\\])`.
- **`planilhas/infra/EscritorCsv.java:55-65`**: proteção contra fórmula no CSV não cobre valores iniciados por tabulação ou `\r`.
- **`comum/web/SegurancaConfig.java:31-33`**: token CSRF sem máscara (usar `XorCsrfTokenRequestAttributeHandler`); toda visita anônima cria sessão.
- **`application.properties`**: `spring.thymeleaf.cache=false` e `fonte-demonstracao=true` na base (mover para o perfil `local`); `dataflow.loja.site` padrão `localhost`; sem `cookie.secure`/`same-site`/`forward-headers-strategy` para produção; propriedades `dataflow.*` sem validação na inicialização.
- **`Dockerfile`**: container roda como root; sem `TZ=America/Sao_Paulo` (o "mês" do painel vira às 21h).
- **`vendas/infra/ArmazenamentoFotos.java:74-80`**: falha ao apagar foto é engolida sem log.
- **`planilhas/infra/LeitorXlsx.java:96`**: `.docx` renomeado para `.xlsx` gera erro 500 em vez de mensagem amigável; `LeitorCsv.java:78` mostra a mensagem técnica do parser ao usuário.
- **`vendas/web/LojaController.java:85-105,159`**: regras de negócio no controller; entrada de 30% duplicada em `DadosLoja.java:55`; slug não remove acentos.
- **`vendas/web/Conversoes.java:14-40`**: status vazio gera NullPointerException; `"-100"` vira 100 km; `"5+2"` lugares vira 52.
- **`vendas/aplicacao/DespesaService.java:44-45,129`**: `31/02/2024` aceito como 29/02 (usar `ResolverStyle.STRICT`); despesa pode cair em anúncio já vendido.
- **`vendas/dominio/SimulacaoFinanciamento.java:46-48`** e **`comum/web/DadosLoja.java:51-58`**: juros de R$ 0,02 aparecem com taxa 0%; taxa negativa na configuração quebra a página do catálogo.
- **`vendas/dominio/Dinheiro.java:47-51`**: verifica o sinal antes de arredondar.
- **`vendas/aplicacao/PainelService.java:99-162`**: cada relatório repete a lista de colunas duas vezes; agregações em memória.
- **`comum/aplicacao/HistoricoService.java:30-33`**: histórico sem paginação e sem coluna de usuário; guarda nome de comprador e placa em texto livre.
- **`vendas/aplicacao/CatalogoDemonstracao.java`** e **`fichas/infra/FonteDemonstracao.java`**: ~450 linhas de dados de exemplo dentro do código de produção.
- **Duplicações**: "7 ou mais lugares" (`FichaTecnica.java:152-156` e `Anuncio.java:222-225`); data brasileira (`DespesaService.java:151-164` e `TipoPadronizacao.java:110-123`); telefone (`DadosLoja`, `Proposta`, `TipoPadronizacao`); slug (`DivulgacaoService`, `LojaController`, `PlanilhaController`).
- **`fichas/aplicacao/FichaService.java:171`**: `if` sobre o tipo da fonte, em vez de método polimórfico na interface.
- **Domínio com anotações JPA**: aceitável neste tamanho; registrar como decisão no SDD (ver ADR-08).
- **Testes**: cenário gigante `fluxoCompletoDaLoja` (90 linhas); dependência do relógio do sistema; README diz 81 testes (são 89).

### 1.5 O que está bom (manter)

- **Domínio rico e encapsulado** `[BOM]`: `CampoFicha` só é alterado por `FichaTecnica`; ficha concluída que fica incompleta volta sozinha para revisão; `Anuncio` protege suas transições; dinheiro em `BigDecimal`.
- **Polimorfismo de livro-texto** `[BOM]`: `RegraTransformacao.aplicar` é `final` e chama `validar` e `transformar` (Template Method); `TipoPadronizacao` carrega a própria função (Strategy); fontes de veículo ordenadas por `@Order` e ligadas por configuração.
- **Inversão de dependência bem feita** `[BOM]`: `VerificadorUsoFicha` é definido em `fichas` e implementado em `vendas`, sem `fichas` conhecer `vendas`.
- **Persistência segura** `[BOM]`: `open-in-view=false`, `ddl-auto=validate`, Flyway.
- **Segurança de base** `[BOM]`: tudo fechado por padrão, CSRF, CSP sem `unsafe-inline`, bcrypt, uploads conferidos por assinatura, fotos com nome UUID fora da pasta pública, sem `th:utext` em nenhum template (verificado), XLSX exportado só com células de texto.
- **Erros amigáveis em pontos certos** `[BOM]`: PDFs e exportações montados em memória antes de responder; importação de despesas lista problemas por linha.
- **Testes** `[BOM]`: nomes que descrevem regras, domínio testado sem mocks, arquivos de teste gerados em código; 89 testes passando.

---

## 2. Edge Case Risk Matrix (Modo Paranoico)

| # | Cenário | Probabilidade | Impacto | Risco | Componente | Arquivo |
|---|---|---|---|---|---|---|
| 1 | Reimportar a planilha de despesas duplica custos; lucro e relatórios ficam errados | Alta | Alto | **CRÍTICO** | DespesaService | `vendas/aplicacao/DespesaService.java:120-149` |
| 2 | API de placa trava dentro da transação, esgota o pool e derruba a loja | Média | Alto | **ALTO** | FonteApiPlaca / FichaService | `fichas/infra/FonteApiPlaca.java:42`; `FichaService.java:65-113` |
| 3 | Mesmo carro anunciado e vendido duas vezes (várias fichas ou clique duplo) | Média | Alto | **ALTO** | AnuncioService | `vendas/aplicacao/AnuncioService.java:58` |
| 4 | PDF público expõe placa e nome do documento | Alta | Médio | **ALTO** | GeradorFichaPdf | `fichas/infra/GeradorFichaPdf.java:45,55,75-77` |
| 5 | Venda concorrente: atualização perdida ou carro `OCULTO` com dados de venda | Baixa | Alto | **ALTO** | Anuncio | `vendas/dominio/Anuncio.java` |
| 6 | Valores do XLSX arredondados ou com sinal invertido; CNPJ em notação científica | Média | Médio | MÉDIO | LeitorXlsx / Dinheiro | `planilhas/infra/LeitorXlsx.java:119-132` |
| 7 | Custo de compra ausente conta como zero e infla o lucro | Alta | Médio | MÉDIO | Rentabilidade | `vendas/dominio/Rentabilidade.java:16` |
| 8 | Duas abas compartilham a área de trabalho e exportam a planilha errada | Média | Médio | MÉDIO | AreaTrabalho | `planilhas/web/AreaTrabalho.java:18-43` |
| 9 | Linha de rótulo do CRLV grava MARCA = ":" e bloqueia o valor certo | Média | Médio | MÉDIO | InterpretadorDocumento | `fichas/dominio/InterpretadorDocumento.java:17,44-55` |
| 10 | Ficha concluída editada muda carro publicado ou vendido sem revisão | Média | Médio | MÉDIO | FichaTecnica | `fichas/dominio/FichaTecnica.java:97-106` |
| 11 | Desfazer venda deixa os leads descartados | Média | Médio | MÉDIO | AnuncioService | `AnuncioService.java:112-130` |
| 12 | Excluir anúncio apaga leads e despesas em cascata | Média | Médio | MÉDIO | Migração V2 | `V2__loja_vendas.sql:34,46` |
| 13 | Fotos órfãs no disco após rollback | Média | Baixo | MÉDIO | ArmazenamentoFotos | `AnuncioService.java:207-223` |
| 14 | Valor > 2.000 caracteres ou TXT em ANSI: erro 500 ou campos ausentes | Média | Baixo | MÉDIO | CampoFicha / Extrator | `CampoFicha.java:37` |
| 15 | Junção por chave repetida ou 20 cópias de desfazer: falta de memória | Baixa | Alto | MÉDIO | ConsolidadorPlanilhas | `ConsolidadorPlanilhas.java:49-51` |
| 16 | Container em UTC: "vendas do mês" zera às 21h | Média | Baixo | BAIXO | PainelService | `PainelService.java:61`; `Dockerfile` |
| 17 | Taxa negativa quebra o catálogo; 0% mostra juros de centavos | Baixa | Baixo | BAIXO | DadosLoja / Simulação | `comum/web/DadosLoja.java:51-58` |
| 18 | Status vazio dá NullPointerException; 31/02 aceito | Baixa | Baixo | BAIXO | Conversoes / DespesaService | `vendas/web/Conversoes.java:37-40` |

---

## 3. Crítica direta + roteiro

### Falhas mais sérias, com exemplo

1. **O sistema confia que ninguém vai repetir uma ação.** Reimportar despesas (A1), clicar duas vezes em "Anunciar" (A5) e abrir duas abas (A7) produzem dados errados sem nenhum erro. O padrão é "verifica, depois grava" sem proteção no banco.
2. **A regra de privacidade é aplicada em um lugar e esquecida em outro.** A página HTML do carro esconde placa e chassi; o PDF da mesma página não (A2). A causa é ter dois caminhos de saída (HTML e PDF) sem um "modelo público" único.
3. **Falhas externas viram silêncio.** API fora do ar vira "nada encontrado" (A4); disco sem permissão vira "404" (M20); pasta temporária sumida vira "arquivo grande demais" (A8). O usuário recebe uma explicação falsa e o log não registra nada.

### Plano 80/20

Com uns 5 a 7 dias de trabalho, os itens A1, A2, A3, A4, A5 e C1 resolvem cerca de 80% do risco real: os únicos que corrompem dados, vazam informação ou derrubam o site.

### Backlog priorizado (15 itens)

| Prioridade | Item | Impacto | Risco de mexer | Esforço |
|---|---|---|---|---|
| 1 | A1 · Importação de despesas idempotente | Alto | Baixo | <1 dia |
| 2 | A2 · PDF público sem placa e sem nomes de arquivo, com teste | Alto | Baixo | <1 dia |
| 3 | C1 · Senha padrão bloqueada fora do perfil local | Alto | Baixo | <1 dia |
| 4 | A3 · Timeout na API de placa (e depois tirar da transação) | Alto | Médio | <1 dia / <1 semana |
| 5 | A5 · `@Version` + checagem por veículo + índice único | Alto | Médio | <1 dia |
| 6 | A4 · "API indisponível" em vez de "nada encontrado" | Médio | Baixo | <1 dia |
| 7 | A10 · CI no GitHub Actions + Dependabot | Médio | Baixo | <1 dia |
| 8 | A6 · Catálogo consulta o estoque uma vez | Alto (em escala) | Baixo | <1 dia |
| 9 | A8 + M18 · Erros de upload e tratador genérico com código de erro | Médio | Baixo | <1 dia |
| 10 | M9 · Lucro nulo quando falta valor de compra | Médio | Baixo | <1 dia |
| 11 | M5 + M6 · Desfazer venda restaura leads; exclusão não apaga leads | Médio | Baixo | <1 dia |
| 12 | A11 · Testes contra PostgreSQL (Testcontainers) | Médio | Baixo | <1 dia |
| 13 | M1 · Quebrar o ciclo `comum ↔ vendas` + ArchUnit | Médio (nota de POO) | Baixo | <1 dia |
| 14 | A7 · Limites de memória e identificador da área de trabalho | Médio | Médio | <1 semana |
| 15 | A9 + M16 · Limite de tentativas no login e no formulário de contato | Médio | Baixo | <1 dia |

### Sinais de alerta

- Documentação desatualizada em relação ao código: README diz 81 testes (são 89), o SDD não descreve o módulo de marketing nem as migrações V3–V5.
- A promessa "placa e chassi não aparecem" não tem teste que a proteja no PDF.
- Nenhum `@Version` em entidades editadas por mais de uma pessoa.

### Ganhos rápidos

**Menos de 1 dia cada**: A1, A2, C1, timeout do A3, A4, primeira parte do A6, A8, M9, M14, `TZ` no Dockerfile, `cache=false` só no perfil local, atualizar README para 89 testes.

**Menos de 1 semana**: A5 completo, A10 + A11, M1 + M2 com ArchUnit, M5 + M6 + M7, A7 (limites e cache).

---

## 4. Painel de especialistas

**Backend sênior**
1. Idempotência na importação e `@Version` nas entidades (A1, A5). *Por quê*: são os únicos defeitos que corrompem dados sem erro. *Risco*: precisa de migração V6.
2. Consultas externas fora da transação (A3). *Risco*: dividir `FichaService.criar` em duas etapas.
3. Separar leitura pública de comandos de admin (M27). *Risco*: mais classes.

**Segurança**
1. Senha padrão e banco exposto (C1, M14). *Por quê*: credenciais conhecidas anulam todo o resto.
2. PDF público e retenção de planilhas com CPF (A2, M13). *Por quê*: LGPD.
3. Limite de tentativas no login e no contato (A9, M16). *Risco*: possível bloqueio do admin real; usar IP + usuário.

**SRE / operação**
1. Timeout na API (A3). *Por quê*: é a única falha externa que derruba a loja.
2. Actuator, healthcheck e código de erro no log (M18, M19).
3. Mensagens de upload honestas e pasta temporária persistente (A8).

**Performance**
1. Catálogo com uma consulta e associações `LAZY` (A6).
2. Limites na área de trabalho e na junção (A7, M15).
3. Índices em `anuncio.ficha_id` e `proposta.anuncio_id` (migração V6).

**Produto**
1. Números do painel confiáveis: duplicidade (A1) e custo zero (M9).
2. Desfazer venda de verdade (M5).
3. "API indisponível" em vez de "nada encontrado" (A4), porque o vendedor repassa isso ao cliente.

**Dev júnior (quem vai manter o código)**
1. CI rodando os testes em todo PR (A10).
2. Testes independentes da ordem (M23) e cenário gigante quebrado em partes.
3. Utilitários únicos para telefone, data, slug e lugares (duplicações em 1.4).

**Plano unificado (resolvendo divergências)**: Performance quer começar pelo catálogo; Backend e Produto querem começar pela integridade dos dados. Integridade vem primeiro, porque dado errado não se recupera e lentidão sim. A primeira parte do A6 (uma consulta só) entra junto por ser trivial. Segurança e SRE convergem em C1 + A3 logo no início. A ordem final é a do backlog acima.

---

## 5. Registros de decisão de arquitetura (ADRs)

**ADR-01 · Importações idempotentes por chave natural**
- *Contexto*: planilhas são reenviadas com frequência (A1).
- *Decisão*: toda importação calcula uma chave por linha e ignora duplicatas, com índice único no banco.
- *Alternativas*: hash do arquivo (não pega planilhas que cresceram); confirmação manual (depende do usuário).
- *Consequências*: reenviar é seguro; duas despesas idênticas no mesmo dia precisam de descrição diferente.
- *Migração*: V6 com o índice; limpar duplicatas existentes antes.

**ADR-02 · Controle otimista de concorrência**
- *Contexto*: vendas e revisões simultâneas (A5).
- *Decisão*: `@Version` em `Anuncio`, `FichaTecnica`, `Proposta`; trava pessimista só no registro de venda.
- *Alternativas*: travar tudo (lento); não tratar (dados perdidos).
- *Consequências*: o segundo a salvar recebe "recarregue a página".
- *Migração*: coluna `versao bigint not null default 0`.

**ADR-03 · Um único "modelo público" do veículo**
- *Contexto*: HTML esconde placa, PDF não (A2).
- *Decisão*: um método no domínio produz a visão pública; HTML e PDF usam só ela.
- *Alternativas*: filtrar em cada saída (como hoje, e já falhou).
- *Consequências*: nova saída pública (ex.: kit de divulgação) herda a regra.
- *Migração*: nenhuma no banco; teste que extrai o texto do PDF.

**ADR-04 · Integrações externas fora de transação e com timeout**
- *Contexto*: A3.
- *Decisão*: consultar, depois abrir transação curta para gravar; timeouts explícitos.
- *Consequências*: API lenta vira "indisponível", não derruba o site.
- *Migração*: dividir `FichaService.criar`/`reconsultar`.

**ADR-05 · Efeitos em disco amarrados ao commit**
- *Contexto*: M7.
- *Decisão*: apagar arquivos em `afterCommit`; limpar gravações em rollback.
- *Consequências*: um arquivo órfão só sobra se o processo cair entre o commit e a limpeza.

**ADR-06 · Módulos sem ciclos, verificados por teste**
- *Contexto*: M1, M2.
- *Decisão*: `comum` não depende de ninguém; módulos só usam `aplicacao` e `dominio` dos outros; regra garantida por ArchUnit.
- *Consequências*: uma dependência proibida quebra o build.

**ADR-07 · Exclusão de anúncio não destrói histórico**
- *Contexto*: M6.
- *Decisão*: bloquear exclusão quando houver leads ou despesas, ou usar exclusão lógica.
- *Consequências*: listagens filtram o novo status; rastreabilidade para LGPD.

**ADR-08 · Domínio anotado com JPA, por pragmatismo**
- *Contexto*: o SDD diz que "o domínio não conhece o banco", mas as entidades têm anotações JPA.
- *Decisão*: manter as anotações e registrar a escolha no SDD.
- *Alternativas*: modelo de persistência separado (dobra o número de classes).
- *Consequências*: o texto do SDD passa a refletir o código.

**ADR-09 · Testes de integração também em PostgreSQL**
- *Contexto*: A11.
- *Decisão*: perfil com Testcontainers no CI; H2 para rodar rápido localmente.
- *Consequências*: precisa de Docker no CI (já existe no GitHub Actions).

**ADR-10 · Configuração validada na inicialização**
- *Contexto*: C1 e propriedades soltas em 1.4.
- *Decisão*: `@ConfigurationProperties` com `@Validated`; falhar ao subir com senha padrão ou URL `localhost` fora do perfil local.
- *Consequências*: deploy mal configurado não sobe, em vez de subir inseguro.

---

## 6. Plano de arquitetura (o que mudar e o que manter)

**Recomendação: não reescrever.** A arquitetura atual (monólito modular com camadas) é adequada ao tamanho do projeto. O plano é de evolução:

1. **Decisões de redesenho**: idempotência e versionamento (ADR-01, 02); visão pública única (ADR-03); integrações fora da transação (ADR-04).
2. **Arquitetura alvo**: os mesmos 5 módulos, com `comum` como folha, portas de entrada (`ArquivoEnviado`, `GeradorPdfFicha`, `ExtratorTexto`) no domínio e um `CatalogoLojaService` só de leitura para o site público.
3. **Mudanças no modelo de dados (V6)**: `versao` nas entidades; índice único de anúncio ativo; índices em `anuncio.ficha_id` e `proposta.anuncio_id`; chave de importação de despesas; registro dos leads afetados por uma venda.
4. **Confiabilidade**: timeouts, efeitos em disco após commit, tratador genérico com código de erro, Actuator.
5. **Segurança**: C1, A2, A9, M13, M14, M16, M17.
6. **Testes**: CI, PostgreSQL, PDF público, uploads negativos, importação de XLSX, `PainelService` com relógio injetado.
7. **Performance**: catálogo com uma consulta; `LAZY` + `@EntityGraph`; limites na área de trabalho.
8. **Experiência do desenvolvedor**: utilitários únicos, dados de demonstração em JSON, README e SDD atualizados.
9. **Caminho incremental**: ver "Strangler fig" abaixo.
10. **O que manter**: todo o domínio, as regras de transformação, a cadeia de fontes, a segurança de base, os testes atuais e o Flyway.

---

## 7. Testes extras de pressão

### Escala 100x (tráfego e equipe)

O que quebra primeiro, em ordem: (1) **o catálogo público** (A6), por consultar o estoque 3 vezes com N+1; (2) **a memória da JVM** pela área de trabalho de planilhas (A7), que derruba a loja junto; (3) **o pool de conexões** se a API de placa travar (A3); (4) **a sessão em memória**, que exige "sticky session" para ter mais de uma instância. Com a equipe dobrada: sem CI, conflitos de merge e regressões chegam à branch principal (A10).

### Custos escondidos

1. **Dados financeiros corrompidos** (A1, M9): cada correção manual exige ler textos de origem um a um.
2. **Depuração às cegas**: quase nada é logado e várias falhas viram mensagens falsas (A4, A8, M20).
3. **Documentação que mente**: README e SDD atrasados confundem quem chega ao projeto.
4. **~450 linhas de dados de demonstração** no código de produção aumentam o que precisa ser revisado.
5. **Testes dependentes da ordem** (M23): cada teste novo precisa escolher uma placa "livre".

### Violações de princípios (SOLID e privilégio mínimo)

- **SRP**: `DivulgacaoService` (M4), `AnuncioService` + `AnuncioController` (M27), `LojaController` com regras de negócio.
- **Inversão de dependência**: `FichaService` depende de `GeradorFichaPdf` e `ExtratorTextoDocumento` concretos; serviços de aplicação dependem de `MultipartFile` (M3); `DespesaService` usa `LeitorXlsx`/`LeitorCsv` concretos em vez da interface `LeitorPlanilha` (M2).
- **Aberto/fechado**: suportar ODS exigiria mexer em `DespesaService`, não só criar um leitor (M2).
- **Polimorfismo trocado por `if`**: `FichaService.java:171` testa o tipo da fonte.
- **Privilégio mínimo**: um único usuário `ADMIN` com acesso a tudo; container como root; banco com senha padrão e porta aberta.

### Migração incremental (strangler fig)

Sem "big bang". Cada passo é um PR que mantém os 89 testes verdes:
1. CI + Dependabot (rede de segurança para o resto).
2. Correções de integridade (A1, A5) com a migração V6.
3. Visão pública única (A2) e senha obrigatória (C1).
4. Timeouts e consulta fora da transação (A3, A4).
5. `CatalogoLojaService` novo ao lado do antigo; trocar o `LojaController` para usá-lo; remover o código antigo.
6. ArchUnit e movimentação de classes para quebrar o ciclo (M1, M2).

### Métricas de sucesso

| Métrica | Hoje | Meta | Como medir |
|---|---|---|---|
| Testes rodando em todo PR | 0% | 100% | GitHub Actions |
| Cobertura do domínio | não medida | ≥ 70% | JaCoCo (`docs/ISSUES.md:105`) |
| Consultas SQL por visita ao catálogo | 3 × (1 + N×3) | ≤ 5, independente do estoque | log de SQL do Hibernate em teste |
| Despesas duplicadas após reimportação | todas | 0 | teste automatizado |
| Tempo até entender um erro relatado | sem código de erro | 1 busca no log pelo código | tratador genérico + ID de requisição |
| Disponibilidade da loja com a API fora | cai após ~10 consultas | 100% | teste com API simulada lenta |

### Antes vs depois

```
ANTES                                            DEPOIS
Navegador ──> LojaController                     Navegador ──> LojaController
               ├─ naLoja() ──┐                                  └─ CatalogoLojaService (1 consulta, LAZY + EntityGraph)
               ├─ vitrine() ─┼─> 3× estoque + N+1
               └─ filtros() ─┘
FichaService [@Transactional]                    FichaService
  └─ API de placa (sem timeout) ─> segura conexão   ├─ consulta fontes (timeout 3s/5s, fora da transação)
                                                    └─ grava [@Transactional curto]
DespesaService ─> salva toda linha               DespesaService ─> ignora linha já importada (chave + índice único)
PDF público ─> filtra só a tabela                HTML e PDF ─> mesma visão pública do domínio
comum ──> vendas ──> comum (ciclo)               comum (folha) <── vendas <── marketing  (ArchUnit garante)
```

### Auditoria de premissas

| Premissa implícita no código | Como validar rápido |
|---|---|
| Ninguém importa a mesma planilha duas vezes | Teste que importa o mesmo arquivo 2× e confere o total |
| A API de placa sempre responde rápido | Teste com servidor simulado que demora 30 s |
| Uma placa tem uma ficha só | Consulta `select placa, count(*) from veiculo join ficha_tecnica ... group by placa having count(*) > 1` |
| Só uma pessoa mexe em cada carro por vez | Teste com duas transações concorrentes |
| Um navegador = uma planilha aberta | Teste com dois fluxos na mesma sessão |
| H2 em modo PostgreSQL se comporta igual ao PostgreSQL | Rodar a suíte com Testcontainers |
| O XLSX mostra o valor real da célula | Teste com célula 1234,56 formatada como `0` |
| O servidor está no fuso de São Paulo | Rodar o teste do painel com `TZ=UTC` às 21h |

### Código para enxugar

- `PainelService.java:99-162`: montar a planilha a partir das chaves da primeira linha remove ~30 linhas e o risco de colunas desalinhadas.
- Criar `comum.dominio.Formatos` (telefone, data, slug, lugares) e eliminar as 4 duplicações listadas em 1.4.
- Mover `CatalogoDemonstracao` e o miolo de `FonteDemonstracao` para JSON em `src/main/resources/demo/` (~450 linhas a menos de Java).
- Regra da entrada de 30% em um só lugar (`SimulacaoFinanciamento`).
- `UsoFichaPelaLoja.java:22`: `existsByFichaId` em vez de carregar a lista inteira.

---

## 8. Resumo executivo

### As 3 ações mais importantes

1. **Tornar a importação de despesas idempotente (A1).** É o único defeito que corrompe os números do negócio no uso normal, sem nenhum aviso.
2. **Fazer o PDF público respeitar a regra de privacidade (A2), com teste.** É um vazamento ativo numa URL pública, contrariando o que o próprio projeto promete.
3. **Bloquear a senha padrão e pôr timeout na API de placa (C1 + A3).** Um transforma o painel em porta aberta; o outro deixa uma API de terceiros derrubar a loja.

### Nível de confiança

| Recomendação | Confiança | O que aumentaria |
|---|---|---|
| A1 (despesas duplicadas) | **Alta**: verificado no código | Teste automatizado reproduzindo |
| A2 (placa no PDF) | **Alta**: verificado no código | Extrair o texto de um PDF real |
| C1 (senha padrão) | **Alta**: verificado em properties, compose e README | — |
| A3 (sem timeout) | **Alta** para a falta de timeout (verificado); **Média** para o impacto no pool | Teste com API simulada lenta |
| A5 (duplo anúncio/concorrência) | **Alta** para a falta de `@Version` e a checagem por ficha (verificado); **Média** para a frequência real | Teste concorrente |
| A6 (N+1 no catálogo) | **Média**: leitura de código, sem medição | Log de SQL com 50 carros |
| M17 (dependências desatualizadas) | **Baixa**: versões confirmadas, CVEs não | OWASP dependency-check |
| A7 (memória da área de trabalho) | **Média**: sem medição de heap | Teste com CSV de 10 MB e perfil de memória |

### Veredito paranoico

**A coisa mais perigosa encontrada é a reimportação de despesas (A1) somada ao custo zero quando falta o valor de compra (M9).** O fluxo documentado no próprio README leva o usuário a reenviar a planilha acumulada todo mês. Na segunda vez, cada custo é contado de novo, lucro e margem passam a mostrar prejuízos que não existem (ou lucros inventados, nos carros sem valor de compra), e esses números vão para o painel e para os relatórios usados em decisões de compra e preço. Não há erro, não há aviso, e a mensagem de sucesso confirma a operação.

---

## Fixing Plan

### Phase 1: Crítico (fazer imediatamente)

- **Achado**: C1 · senha padrão aceita e publicada (`application.properties:29-30`, `SegurancaConfig.java:58-63`, `docker-compose.yml:23-27`)
  - **Correção**: remover o padrão do `@Value`; lançar exceção na inicialização se a senha for `dataflow123` ou < 12 caracteres fora do perfil `local`; manter um padrão só em `application-local.properties`; exigir a variável no compose; tirar a senha do README (deixar só a instrução do perfil local).
  - **Esforço**: <1 dia
  - **Arquivos**: `src/main/resources/application.properties`, `src/main/resources/application-local.properties`, `comum/web/SegurancaConfig.java`, `docker-compose.yml`, `README.md`

### Phase 2: Alto (nesta sprint)

- **A1** · importação de despesas duplica custos (`DespesaService.java:120-149`)
  - **Correção**: chave natural por linha; consulta de existência antes do `save`; "Linha N: já importada" em `problemas`; índice único na V6; teste que importa 2×.
  - **Esforço**: <1 dia · **Arquivos**: `vendas/aplicacao/DespesaService.java`, `vendas/infra/DespesaRepository.java`, `db/migration/V6__integridade.sql`, `src/test/.../vendas/LojaIntegracaoTest.java`
- **A2** · placa e nome de arquivo no PDF público (`GeradorFichaPdf.java:45,55,75-77`)
  - **Correção**: quando `publico`, título sem placa, "Final da placa X", sem rodapé de fontes; teste com PDFBox.
  - **Esforço**: <1 dia · **Arquivos**: `fichas/infra/GeradorFichaPdf.java`, teste novo `GeradorFichaPdfTest`, `LojaIntegracaoTest.java`
- **A3** · API sem timeout e dentro de transação (`FonteApiPlaca.java:42`, `FichaService.java:65-113`)
  - **Correção**: `RestClient.Builder` injetado com timeouts; separar consulta (fora) e gravação (transação curta).
  - **Esforço**: <1 dia (timeout) + <1 semana (separação) · **Arquivos**: `fichas/infra/FonteApiPlaca.java`, `fichas/aplicacao/FichaService.java`
- **A4** · falha da API vira "nada encontrado" (`FonteApiPlaca.java:77-80`, `FichaService.java:174-178`, `FichaController.java:100-102`)
  - **Correção**: resultado com indicador de falha; "API indisponível" na ficha e aviso na tela.
  - **Esforço**: <1 dia · **Arquivos**: `fichas/dominio/FonteDadosVeiculo.java`, `fichas/infra/FonteApiPlaca.java`, `fichas/aplicacao/FichaService.java`, `fichas/web/FichaController.java`
- **A5** · anúncio duplicado e atualização perdida (`AnuncioService.java:58`, `Anuncio.java`, `FichaTecnica.java`)
  - **Correção**: checagem por veículo; índice único parcial; `@Version` em 3 entidades; mensagem para conflito de versão; trava na venda.
  - **Esforço**: <1 dia · **Arquivos**: `vendas/aplicacao/AnuncioService.java`, `vendas/infra/AnuncioRepository.java`, `vendas/dominio/Anuncio.java`, `fichas/dominio/FichaTecnica.java`, `vendas/dominio/Proposta.java`, `comum/web/TratadorErros.java`, `db/migration/V6__integridade.sql`
- **A6** · catálogo consulta o estoque 3× (`LojaController.java:78-83`, `AnuncioService.java:165-200`)
  - **Correção**: passo 1, uma consulta só; passo 2, `LAZY` + `@EntityGraph` + índice de campos.
  - **Esforço**: <1 dia + <1 semana · **Arquivos**: `vendas/web/LojaController.java`, `vendas/aplicacao/AnuncioService.java`, `vendas/dominio/Anuncio.java`, `fichas/dominio/FichaTecnica.java`, `vendas/infra/AnuncioRepository.java`
- **A7** · memória e abas da área de trabalho (`AreaTrabalho.java`, `PlanilhaController.java`, `ConsolidadorPlanilhas.java:49-51`)
  - **Correção**: teto de linhas e de resultado da junção; cache por id; identificador no formulário; desfazer por regras.
  - **Esforço**: <1 semana · **Arquivos**: `planilhas/web/AreaTrabalho.java`, `planilhas/web/PlanilhaController.java`, `planilhas/dominio/ConsolidadorPlanilhas.java`, `planilhas/infra/LeitorCsv.java`, `planilhas/infra/LeitorXlsx.java`
- **A8** · erro de upload mascarado (`TratadorErros.java:28-32`)
  - **Correção**: separar tamanho de outros erros; mensagem a partir da configuração; log; `server.tomcat.basedir` persistente.
  - **Esforço**: <1 dia · **Arquivos**: `comum/web/TratadorErros.java`, `src/main/resources/application.properties`
- **A9** · login sem limite de tentativas (`SegurancaConfig.java:38-41`)
  - **Correção**: contador por IP + usuário; log de falhas.
  - **Esforço**: <1 dia · **Arquivos**: `comum/web/SegurancaConfig.java`, classe nova de controle de tentativas
- **A10** · sem CI (`.github/workflows/` ausente, `Dockerfile:6`)
  - **Correção**: `ci.yml` com `./mvnw -B verify` e build do Docker; Dependabot; proteção de branch.
  - **Esforço**: <1 dia · **Arquivos**: `.github/workflows/ci.yml`, `.github/dependabot.yml`, `Dockerfile`
- **A11** · testes só em H2 (`src/test/resources/application.properties:1`)
  - **Correção**: Testcontainers num perfil Maven; alinhar `DEFAULT_NULL_ORDERING`.
  - **Esforço**: <1 dia · **Arquivos**: `pom.xml`, `src/test/resources/application.properties`, configuração de teste nova

### Phase 3: Médio (próxima sprint)

- **M1** ciclo `comum ↔ vendas` → mover `DadosLoja`; ArchUnit. *<1 dia* · `comum/web/DadosLoja.java`, `marketing/aplicacao/DivulgacaoService.java`, teste ArchUnit novo
- **M2** acesso ao `infra` de outros módulos → `LeitorPlanilha` com opções; `FichaService.existePlaca`. *<1 dia* · `vendas/aplicacao/DespesaService.java`, `vendas/aplicacao/DemonstracaoService.java`, `marketing/aplicacao/DivulgacaoService.java`, `planilhas/dominio/LeitorPlanilha.java`
- **M3** `MultipartFile` e classes concretas na aplicação → `ArquivoEnviado`, interfaces de PDF e extrator. *<1 semana* · `vendas/aplicacao/AnuncioService.java`, `planilhas/aplicacao/PlanilhaService.java`, `fichas/aplicacao/FichaService.java`, `comum/aplicacao/ArmazenamentoService.java`
- **M4** `DivulgacaoService` com muitas responsabilidades → dividir; enum por `==`. *<1 semana* · `marketing/aplicacao/*`, `marketing/web/DivulgacaoController.java`
- **M5** desfazer venda não restaura leads → registrar e restaurar; tabela de transições. *<1 dia* · `vendas/aplicacao/AnuncioService.java`, `vendas/dominio/Venda.java`, `vendas/dominio/Proposta.java`, `vendas/dominio/StatusProposta.java`
- **M6** exclusão em cascata apaga leads e despesas → bloquear ou exclusão lógica. *<1 dia* · `vendas/aplicacao/AnuncioService.java`, V6
- **M7** arquivos fora do destino da transação → `afterCommit` / rollback; validar antes de gravar. *<1 dia* · `vendas/aplicacao/AnuncioService.java`, `comum/aplicacao/ArmazenamentoService.java`, `vendas/infra/ArmazenamentoFotos.java`
- **M8** valores do XLSX formatados → leitura bruta para números; parênteses. *<1 dia* · `planilhas/infra/LeitorXlsx.java`, `vendas/dominio/Dinheiro.java`
- **M9** custo zero sem valor de compra → lucro nulo e fora dos totais. *<1 dia* · `vendas/dominio/Rentabilidade.java`, `vendas/aplicacao/PainelService.java`
- **M10** edição de ficha concluída → voltar para revisão; bloquear se vendido. *<1 dia* · `fichas/dominio/FichaTecnica.java`
- **M11** rótulo do CRLV sozinho → exigir valor alfanumérico; testes. *<1 dia* · `fichas/dominio/InterpretadorDocumento.java`, `InterpretadorDocumentoTest.java`
- **M12** valores longos e TXT em ANSI → truncar/rejeitar; detecção de codificação. *<1 dia* · `fichas/dominio/CampoFicha.java`, `fichas/infra/ExtratorTextoDocumento.java`
- **M13** retenção de planilhas com CPF → exclusão e limpeza periódica. *<1 semana* · `planilhas/web/PlanilhaController.java`, `comum/aplicacao/ArmazenamentoService.java`
- **M14** banco exposto → remover `ports:` ou `127.0.0.1`; senha obrigatória. *<1 dia* · `docker-compose.yml`, `application.properties`
- **M15** arquivos-bomba → limites do POI, teto de linhas, PDFBox com memória limitada. *<1 semana* · `planilhas/infra/LeitorXlsx.java`, `LeitorCsv.java`, `fichas/infra/ExtratorTextoDocumento.java`
- **M16** contato sem limite → honeypot, tempo mínimo, limite por IP. *<1 dia* · `vendas/web/LojaController.java`, `templates/fragmentos/contato.html`, filtro novo
- **M17** dependências (a confirmar) → rodar scanner; atualizar parent e POI. *<1 dia* · `pom.xml`
- **M18** sem tratador genérico → `@ExceptionHandler(Exception.class)` com código de erro. *<1 dia* · `comum/web/TratadorErros.java`, `templates/error.html`
- **M19** sem health/métricas → Actuator protegido, healthcheck, ID de requisição. *<1 dia* · `pom.xml`, `comum/web/SegurancaConfig.java`, `docker-compose.yml`, filtro novo
- **M20** erro de disco vira 404 → só `NoSuchFileException` vira 404. *<1 dia* · `vendas/infra/ArmazenamentoFotos.java`, `comum/aplicacao/ArmazenamentoService.java`, `comum/dominio/RecursoNaoEncontradoException.java`
- **M21** importação em lote com mensagem enganosa → validar antes ou por arquivo. *<1 dia* · `planilhas/aplicacao/PlanilhaService.java`
- **M22** log da API com placa/RENAVAM → logar tipo e status; exigir https. *<1 dia* · `fichas/infra/FonteApiPlaca.java`
- **M23** testes dependentes da ordem → limpar tabelas após cada teste. *<1 dia* · `src/test/.../LojaIntegracaoTest.java`, `FluxosIntegracaoTest.java`
- **M24** teste de path traversal → nomes inválidos esperando 404; teste unitário de `abrir`. *<1 dia* · `LojaIntegracaoTest.java`, teste novo `ArmazenamentoFotosTest`
- **M25** verificações negativas frágeis → modelo ou texto visível. *<1 dia* · `LojaIntegracaoTest.java`
- **M26** cobertura não medida → JaCoCo com 70% no domínio. *<1 dia* · `pom.xml`
- **M27** `AnuncioService`/`AnuncioController` grandes → `CatalogoLojaService`, `DespesaController`, `VendaForm`. *<1 semana* · `vendas/aplicacao/AnuncioService.java`, `vendas/web/AnuncioController.java`

### Phase 4: Baixo (quando mexer nesses arquivos)

- **`comum/web/TratadorErros.java`**: validar caminho do Referer.
- **`comum/web/SegurancaConfig.java`**: `XorCsrfTokenRequestAttributeHandler`; sessão anônima.
- **`planilhas/infra/EscritorCsv.java`**: incluir `\t` e `\r` na proteção contra fórmula.
- **`planilhas/infra/LeitorXlsx.java`** e **`LeitorCsv.java`**: capturar `POIXMLException`/`RecordFormatException`; não mostrar mensagem técnica; avisar colunas além do cabeçalho.
- **`src/main/resources/application.properties`**: mover `cache=false` e `fonte-demonstracao` para `local`; cookies seguros e `forward-headers-strategy` num perfil `prod`; `@ConfigurationProperties` validado.
- **`Dockerfile`**: usuário não-root; `TZ=America/Sao_Paulo`.
- **`vendas/infra/ArmazenamentoFotos.java`**: log na falha ao apagar.
- **`vendas/web/LojaController.java`** e **`comum/web/DadosLoja.java`**: tirar regras do controller; entrada de 30% única; slug sem acento; `parcelaDesde` tolerante a taxa inválida.
- **`vendas/web/Conversoes.java`**: status vazio; números negativos/decimais; lugares "5+2".
- **`vendas/aplicacao/DespesaService.java`**: `ResolverStyle.STRICT`; preferir anúncio não vendido.
- **`vendas/dominio/SimulacaoFinanciamento.java`**: juros não negativos com taxa 0.
- **`vendas/dominio/Dinheiro.java`**: arredondar antes de checar sinal.
- **`vendas/aplicacao/PainelService.java`**: colunas a partir das chaves; relógio injetado.
- **`comum/aplicacao/HistoricoService.java`**: paginação; coluna de usuário; menos dado pessoal em texto livre.
- **`vendas/aplicacao/CatalogoDemonstracao.java`** e **`fichas/infra/FonteDemonstracao.java`**: dados para JSON.
- **`fichas/aplicacao/FichaService.java:171`**: método `detalhe()` na interface de fonte.
- **`vendas/aplicacao/UsoFichaPelaLoja.java:22`**: `existsByFichaId`.
- **Utilitários duplicados**: criar `comum.dominio.Formatos` e reaproveitar.
- **Documentação**: README (89 testes, módulo de marketing), SDD (marketing, V3–V5, ADR-08).
- **Testes**: quebrar `fluxoCompletoDaLoja`; relógio injetado.

### Dependency graph

- A5 depende da migração V6, que também recebe A1 e M6: fazer as três juntas.
- A4 depende de A3 (ambas mudam o contrato de `FonteApiPlaca`; injetar o `RestClient.Builder` primeiro).
- A11 depende de A10 (o perfil PostgreSQL roda no CI).
- M26 (JaCoCo) depende de A10 para ser verificado automaticamente.
- M18 deve vir antes de A5 (a mensagem de conflito de versão usa o tratador).
- M27 (`CatalogoLojaService`) é o lugar natural para o passo 2 de A6.
- M1 e M2 ficam mais seguros depois de A10 (ArchUnit roda no CI).
- M24 e o teste do PDF público acompanham A2.

### Estimated total effort

- Phase 1: 0,5 dia
- Phase 2: 7 a 9 dias
- Phase 3: 10 a 14 dias
- Phase 4: 3 a 4 dias (oportunista)
- **Total**: cerca de 21 a 28 dias de trabalho de uma pessoa
