# Contexto da sessão anterior (01/11/2026)

Resumo do que já foi feito neste projeto (DataFlow Auto, Java 21 + Spring Boot 3.4.1) antes de continuar no Claude Code.

## Ambiente
- Projeto em `Documentos\dataflow-auto` (Windows). JDK 21 (Temurin) instalado, com `JAVA_HOME` configurado.
- A loja roda no perfil local (banco H2 em arquivo): `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`, ou `Ctrl+Shift+B` no VS Code. Ela já pode estar rodando na porta 8080.
- Endereços: loja em http://localhost:8080, painel em http://localhost:8080/painel (no perfil local: `admin` / `dataflow123`).
- Testes: `mvnw.cmd test`. Hoje são **94 testes, todos passando**.

## Ajustes visuais
- `static/css/loja.css`: as fotos dos carros usam `object-fit: contain` (antes era `cover`, que cortava). Vale para os cards do catálogo, o destaque do topo, a galeria e as miniaturas.
- A foto do BYD Dolphin foi editada para tirar a placa (placa em branco, igual às outras), em `dados/fotos/92e121ff-...jpg` e em `exemplos/fotos-demonstracao/BYD1Q52.jpg`.

## Revisão completa do código
- O relatório está em `grill-report-2026-11-01.md`, na raiz do projeto. Os itens têm códigos (C1, A1–A11, M1–M27, itens baixos) e, no fim, um plano de correção em 4 fases.
- Resumo: projeto sólido. Os riscos reais estavam em integridade de dados, privacidade no PDF público, senha padrão e na API externa sem timeout.

## Itens do relatório já corrigidos
1. **A1, despesas duplicadas na reimportação** (`vendas/aplicacao/DespesaService.java`, `vendas/dominio/Despesa.java`)
   - Novo `Despesa.chaveLancamento()`, que compara data, categoria, valor e descrição (sem diferenciar maiúsculas e minúsculas), sem olhar a origem.
   - A importação ignora linhas que o carro já tinha antes dela e avisa "Linha N: já estava lançada...".
   - Linhas iguais dentro da mesma planilha continuam sendo importadas, de propósito: podem ser compras iguais de verdade.
2. **A2, placa e nomes de arquivo no PDF público** (`fichas/infra/GeradorFichaPdf.java`, `fichas/dominio/Placa.java`)
   - Novo `Placa.finalDaPlaca()`.
   - Quando `publico=true`: o título dos metadados não leva a placa, o subtítulo mostra "Final da placa X" e o rodapé "Fontes consultadas" não aparece.
   - O PDF interno continua igual.
3. **C1, senha padrão do painel** (`comum/web/SegurancaConfig.java`, `application.properties`, `application-local.properties`, `src/test/resources/application.properties`, `docker-compose.yml`, `README.md`)
   - `SegurancaConfig.validarSenha` impede a inicialização sem `DATAFLOW_ADMIN_SENHA`, com a senha `dataflow123` ou com senha de menos de 10 caracteres.
   - A senha padrão só é aceita quando `dataflow.admin.senha-padrao-permitida=true`, o que vale apenas no perfil local e nos testes.
4. **A3 (parte 1), timeout na API de placa** (`fichas/infra/FonteApiPlaca.java`)
   - `SimpleClientHttpRequestFactory` com 3 s para conectar e 5 s para ler.
   - Um construtor extra recebe os tempos (usado nos testes).
   - O log de falha mostra só o tipo do erro, sem a URL com placa e RENAVAM.

5. **A4, API de placa fora do ar parecia "0 encontrados"** (`fichas/dominio/FonteIndisponivelException.java`, `FonteDadosVeiculo.java`, `FichaTecnica.java`, `fichas/infra/FonteApiPlaca.java`, `fichas/aplicacao/FichaService.java`, `fichas/web/FichaController.java`, `templates/fichas/revisar.html`)
   - Quando falha, `FonteApiPlaca` lança `FonteIndisponivelException` em vez de devolver um mapa vazio.
   - `FichaService.consultarFontes` registra "API de consulta por placa (indisponível, não consultada)" nas fontes da ficha.
   - `FichaTecnica.consultaIncompleta()` mostra um aviso fixo na tela da ficha. Criar ou reconsultar com falha mostra um alerta, e a mensagem "nenhuma informação nova" não aparece mais.
   - Teste `FichaFontesIntegracaoTest.falhaDaApiFicaRegistradaNaFichaEAvisadaNaTela` (contexto próprio, API em `localhost:1`) e `FichaTecnicaTest.fonteQueNaoRespondeuDeixaAConsultaIncompleta`.
6. **A3 (parte 2), consulta às fontes fora da transação** (`fichas/aplicacao/FichaService.java`)
   - `criar` e `reconsultar` não têm mais `@Transactional`. Agora funcionam em 3 passos: guardar o documento (transação própria do `ArmazenamentoService`), `buscarNasFontes` sem transação e, por fim, `aplicarRespostas` + gravar num `TransactionTemplate` curto.
   - `reconsultar` relê a ficha dentro da transação, porque alguém pode ter revisado enquanto a API respondia.
   - Quando chamados por dentro de outra transação (ex.: `DemonstracaoService.carregar`), continuam participando dela. Só afeta a carga de demonstração.
   - Teste `FichaFontesIntegracaoTest.fontesSaoConsultadasForaDaTransacao`: uma fonte espiã confere `TransactionSynchronizationManager.isActualTransactionActive()`.
   - `docs/SDD.md` atualizado (contagem e linha das fontes).
7. **A5, anúncio duplicado e alterações simultâneas** (`db/migration/V6__integridade.sql`, `vendas/dominio/Anuncio.java`, `vendas/dominio/Proposta.java`, `fichas/dominio/FichaTecnica.java`, `vendas/infra/AnuncioRepository.java`, `vendas/aplicacao/AnuncioService.java`, `comum/web/TratadorErros.java`)
   - V6: coluna `versao` em `anuncio`, `ficha_tecnica` e `proposta` (`@Version`). Coluna `anuncio.veiculo_em_andamento_id`, com UNIQUE e chave estrangeira para `veiculo`, preenchida só enquanto o anúncio não foi vendido. O H2 não aceita índice único parcial, por isso a coluna. A migração marca só o anúncio mais recente de cada carro, para não quebrar bases que já tenham duplicados.
   - `Anuncio` preenche a coluna ao ser criado, limpa na venda e preenche de novo ao desfazer a venda.
   - `AnuncioService.criar` checa por veículo (`existsByVeiculoEmAndamentoId`) e usa `saveAndFlush`, transformando `DataIntegrityViolationException` em "já tem um anúncio em andamento". `desfazerVenda` recusa quando o carro já foi anunciado de novo. `fichasProntasParaAnunciar` também filtra por veículo. O antigo `existsByFichaIdAndStatusNot` foi removido.
   - `TratadorErros` responde a `ObjectOptimisticLockingFailureException` com um aviso, em vez de mostrar a página de erro.
   - Teste novo `vendas/IntegridadeIntegracaoTest` (4 testes, reaproveita o contexto do `LojaIntegracaoTest`).
   - Total: **101 testes passando**. A migração só foi testada no H2, ainda não no PostgreSQL.
8. **A10, CI** (`.github/workflows/ci.yml`, `.github/dependabot.yml`, `Dockerfile`, `README.md`)
   - Job "Testes (mvnw verify)": Temurin 21 com cache do Maven, `chmod +x mvnw` e `./mvnw -B verify`. Se falhar, publica os relatórios do surefire.
   - Job "Imagem Docker": `docker build`, que só roda depois dos testes (`needs`). O Dockerfile continua com `-DskipTests` e um comentário explicando por quê.
   - Dependabot semanal para maven (com o Spring Boot agrupado), github-actions e docker.
   - `./mvnw -B verify` passa localmente. O build da imagem **não foi testado**: não há Docker nesta máquina.
   - **Ainda não roda**: a pasta não é um repositório git (sem `.git`, e o git não está instalado). Falta subir para o GitHub e ativar a proteção de branch (Settings > Branches > "Require status checks").
9. **A11, testes contra PostgreSQL** (`pom.xml`, `src/test/resources/application.properties`, `FichaFontesIntegracaoTest`, `ci.yml`, README, SDD, CLAUDE.md)
   - Dependência `org.testcontainers:postgresql` (escopo test; versão do Spring Boot) e perfil Maven `postgres`. O perfil passa ao surefire `spring.datasource.url=jdbc:tc:postgresql:16:///dataflow_teste` com o `ContainerDatabaseDriver`.
   - URL do H2 de teste com `DEFAULT_NULL_ORDERING=HIGH`, igual ao perfil local.
   - `FichaFontesIntegracaoTest` não troca mais `spring.datasource.url`, porque propriedades do teste venceriam o perfil.
   - Job novo no CI "Testes no PostgreSQL" (`./mvnw -B verify -Ppostgres`). A imagem Docker espera os dois jobs de teste.
   - Com H2: 101 testes passando na época. Com `-Ppostgres` nesta máquina: o perfil liga certo, mas para em "Could not find a valid Docker environment", porque não há Docker. **Os testes nunca rodaram de verdade no PostgreSQL**: a primeira vez vai ser no CI, e pode aparecer alguma diferença entre H2 e PostgreSQL pra corrigir.
10. **A6 (passo 1), catálogo lendo o estoque 3 vezes** (`vendas/aplicacao/AnuncioService.java`, `vendas/web/LojaController.java`, `application.properties` principal e de teste)
   - `AnuncioService.catalogo(filtro)` lê o estoque uma vez e devolve o record `Catalogo(naLoja, resultado, opcoes)`. `vitrine()` e `opcoesFiltro()` públicos foram removidos.
   - `spring.jpa.properties.hibernate.default_batch_fetch_size=50`: fichas, campos e fotos vêm em lotes, não carro por carro. Os mapeamentos continuam `EAGER`.
   - Teste novo `vendas/CatalogoConsultasTest`, que conta as consultas com as estatísticas do Hibernate. Antes: 15 com 2 carros e 33 com 5. Agora: 3 nos dois casos.
   - Total: 102 testes passando na época (H2).
11. **A7, área de trabalho de planilhas** (`planilhas/dominio/Planilha.java`, `ConsolidadorPlanilhas.java`, `planilhas/infra/LeitorCsv.java`, `LeitorXlsx.java`, `Cabecalhos.java`, `planilhas/aplicacao/PlanilhaService.java`, `planilhas/web/AreaTrabalho.java`, `PlanilhaController.java`, `templates/planilhas/trabalho.html`)
   - Teto `Planilha.MAXIMO_LINHAS = 200_000` nos leitores (CSV e XLSX, `ArquivoInvalidoException`) e na combinação (`RegraNegocioException`, conferido a cada linha adicionada, então a junção N×M para antes de estourar a memória). Construtores extras recebem um teto menor, usados nos testes.
   - `PlanilhaService.carregar` guarda as últimas 4 planilhas lidas (LRU, por id do arquivo). O arquivo nunca muda depois de importado e a `Planilha` é imutável.
   - `AreaTrabalho`: guarda a planilha de partida, a atual e as etapas (`record Etapa(descricao, UnaryOperator<Planilha>)`). Desfazer reaplica as etapas a partir da partida. Com mais de 20 etapas, a mais antiga vira parte da partida. Antes eram até 21 cópias por sessão; agora são 2.
   - `PlanilhaService.aplicarRegras` e `juntar` são as versões sem histórico, usadas nas etapas (pra o desfazer não registrar a operação de novo).
   - Identificador `area` (UUID novo a cada planilha aberta) nos formulários de organizar, combinar e desfazer e nos links de exportar. `AreaTrabalho.conferir` recusa formulário de outra aba com uma mensagem.
   - Testes novos: `planilhas/AreaTrabalhoTest` (3), `ConsolidadorPlanilhasTest.combinacaoQuePassaDoTetoDeLinhasEhRecusada`, `LeitoresEscritoresTest.planilhaComMaisLinhasQueOTetoEhRecusada` e `FluxosIntegracaoTest.abaAntigaNaoAlteraNemExportaAPlanilhaAbertaEmOutraAba`. O fluxo existente agora manda `area`.
   - Total: 108 testes passando na época (H2). SDD, README e CLAUDE.md atualizados.
12. **A8, mensagem de erro de upload** (`comum/web/TratadorErros.java`, `application.properties`, README, SDD)
   - Só `MaxUploadSizeExceededException` vira "passou do tamanho". A mensagem usa `spring.servlet.multipart.max-file-size` e `max-request-size` (hoje "até 10 MB por arquivo e 50 MB por envio").
   - As outras `MultipartException` vão para o log (`LOG.error` com a exceção) e mostram "Não foi possível receber o arquivo enviado...".
   - `server.tomcat.basedir=${DATAFLOW_TOMCAT_DIR:./dados/tomcat}`: o Tomcat e os temporários dos uploads ficam fora de `/tmp`. No Docker, ficam dentro do volume `/app/dados`.
   - Teste novo `comum/web/TratadorErrosTest` (2 testes; usa `OutputCaptureExtension` para conferir o log).
   - Conferido subindo o `.jar` numa pasta temporária: a pasta `dados/tomcat/work/...` é criada e a loja responde 200.
   - Total: 110 testes passando na época (H2).
13. **A9, login contra força bruta** (`comum/web/ControleTentativasLogin.java` novo, `comum/web/FiltroBloqueioLogin.java` novo, `SegurancaConfig.java`, `templates/entrar.html`, `application.properties`)
   - `ControleTentativasLogin` (`@Component`, em memória, com `Clock` injetável): chave = IP + usuário em minúsculas. 5 falhas dentro de 15 min bloqueiam o par por 15 min. Login certo zera. Acima de 1.000 registros, os vencidos são limpos. Configurável por `dataflow.seguranca.login.maximo-falhas` e `janela-minutos`.
   - `FiltroBloqueioLogin` (não é `@Component`, para não virar filtro de servlet também) fica antes do `UsernamePasswordAuthenticationFilter` e redireciona para `/entrar?bloqueado` sem conferir a senha.
   - `SegurancaConfig`: `successHandler` (zera e segue para a página pedida ou `/painel`) e `failureHandler` (conta, loga com IP e usuário via `paraLog`, que tira caracteres de controle e corta em 60, e redireciona para `?error` ou `?bloqueado`).
   - `entrar.html`: aviso para `?bloqueado`.
   - O IP usado é `request.getRemoteAddr()`. Atrás de um proxy reverso (nginx, Cloudflare), todos chegariam com o IP do proxy: aí é preciso configurar `server.forward-headers-strategy=native` (só com proxy confiável).
   - Testes novos: `comum/web/ControleTentativasLoginTest` (5) e `LojaIntegracaoTest.depoisDeCincoSenhasErradasNemASenhaCertaEntra`, com IP próprio para não bloquear os outros testes.
   - Total: **116 testes passando** (H2).

## Testes novos
- `LojaIntegracaoTest`: reimportar a mesma planilha não duplica despesas. O PDF público é lido com PDFBox para conferir que não tem a placa nem "Fontes consultadas".
- `VendasDominioTest.mesmaDespesaTemAMesmaChaveIndependenteDaOrigem`
- `FontesDadosTest.apiPlacaTravadaNaoSeguraARequisicao` (servidor que nunca responde)
- `comum/web/SegurancaConfigTest` (3 testes)

## Próximos passos sugeridos (em ordem)
1. ~~**A4**~~ (feito).
2. ~~**A3 (parte 2)**~~ (feito).
3. ~~**A5**~~ (feito).
4. ~~**A10**~~ e ~~**A11**~~ (feitos; falta subir o projeto para o GitHub).
5. ~~**A6**~~ passo 1 (feito). Os passos 2 (LAZY + `@EntityGraph`) e 3 (filtrar e paginar no SQL) ficam para quando o estoque crescer.
6. ~~**A7**~~ (feito).
7. ~~**A8**~~ (feito).
8. ~~**A9**~~ (feito). **Todos os itens C e A do relatório estão corrigidos** (A6 só o passo 1).
9. Próximo: os itens médios (M1–M27) do `grill-report-2026-11-01.md`, e subir o projeto para o GitHub para o CI rodar.
5. Depois disso, seguir as fases do `grill-report-2026-11-01.md`.

## Preferências do dono do projeto
- Conversa em português, num tom descontraído.
- Uma correção por vez, explicando o que mudou e rodando os testes no final de cada uma.
