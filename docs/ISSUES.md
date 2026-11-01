# Backlog — DataFlow Auto (4 sprints)

Pronto para virar issues no GitHub Projects. Cada issue tem estimativa em pontos (1, 2, 3, 5, 8), prioridade (Alta, Média, Baixa), labels e critério de aceite. Os RFs se referem a `docs/SDD.md`.

**Campos sugeridos no GitHub Projects:** Status (Todo, In Progress, In Review, Done), Sprint (iteração), Estimativa (número), Prioridade (single select).
**Labels sugeridas:** `infra`, `docs`, `planilhas`, `fichas`, `front`, `backend`, `banco`, `teste`, `ia`.
**Fluxo:** uma branch por issue (`feat/12-importar-xlsx`), PR referenciando a issue (`Closes #12`), revisão de outro integrante e merge.

---

## Sprint 1 — Modelagem, arquitetura e estrutura (18/10 a 25/10)

| # | Issue | Labels | Est. | Prior. |
|---|---|---|---|---|
| 1 | Criar repositório, README, `.gitignore` e licença | infra, docs | 1 | Alta |
| 2 | Configurar GitHub Projects com 4 sprints, campos e labels | infra | 1 | Alta |
| 3 | Preencher `docs/SDD.md` para o SeNAS (problema, escopo, RFs, arquitetura, entidades, IA) | docs | 3 | Alta |
| 4 | Criar projeto Spring Boot (Maven, Java 21) com a estrutura de pacotes modular | backend, infra | 2 | Alta |
| 5 | Configurar PostgreSQL com Docker Compose e Flyway (migração inicial) | banco, infra | 3 | Alta |
| 6 | Implementar classes de domínio: `Planilha`, `Coluna`, `Linha` | planilhas, backend | 3 | Alta |
| 7 | Implementar classes de domínio: `Veiculo`, `FichaTecnica`, `CampoFicha` e enums | fichas, backend | 3 | Alta |
| 8 | Definir interfaces `LeitorPlanilha`, `EscritorPlanilha`, `FonteDadosVeiculo` e a classe abstrata `RegraTransformacao` | backend | 2 | Alta |
| 9 | Desenhar diagramas (arquitetura, classes, ER) e incluir no SDD | docs | 2 | Média |
| 10 | Criar layout base das páginas (Thymeleaf + CSS) e tela inicial com os dois módulos | front | 2 | Média |
| 11 | Configurar CI no GitHub Actions (build + testes) | infra, teste | 2 | Média |

**Critérios de aceite principais**
- #4/#5: `docker compose up -d` e `./mvnw spring-boot:run` sobem a aplicação conectada ao banco.
- #6/#7: classes com atributos privados, construtores válidos e testes unitários básicos.
- #8: interfaces compilam e têm pelo menos uma implementação *stub* para teste.

**Total: 24 pontos**

---

## Sprint 2 — Importação, visualização, organização e validação (26/10 a 01/11)

| # | Issue | Labels | Est. | Prior. | RF |
|---|---|---|---|---|---|
| 12 | Implementar `LeitorXlsx` com Apache POI | planilhas, backend | 3 | Alta | RF01 |
| 13 | Implementar `LeitorCsv` (separador `;` e `,`, UTF-8 e ISO-8859-1) | planilhas, backend | 3 | Alta | RF01 |
| 14 | Criar `ImportacaoService` com validação de extensão, MIME e tamanho | planilhas, backend | 3 | Alta | RF01, RNF02 |
| 15 | Tela de upload com mensagens de erro claras | front | 2 | Alta | RF01 |
| 16 | Tela de pré-visualização da planilha (primeiras 50 linhas, paginação) | front | 3 | Alta | RF02 |
| 17 | Regras `SelecionarColunas`, `ReordenarColunas` e `RenomearColuna` | planilhas, backend | 3 | Alta | RF03 |
| 18 | Tela para escolher, arrastar e renomear colunas | front | 5 | Alta | RF03 |
| 19 | Regra `RemoverDuplicatas` e relatório de campos vazios | planilhas, backend | 3 | Média | RF05 |
| 20 | Regra `PadronizarTexto` (trim, maiúsculas, CPF, placa, data) | planilhas, backend | 3 | Média | RF06 |
| 21 | Armazenamento seguro dos arquivos enviados e entidade `ArquivoImportado` | backend, banco | 2 | Média | RNF03 |
| 22 | Testes unitários dos leitores e das regras | teste | 3 | Alta | RNF05 |

**Critérios de aceite principais**
- Arquivo inválido (ex.: `.exe`, maior que 10 MB) é rejeitado com mensagem explicativa.
- Uma planilha XLSX e uma CSV de exemplo são exibidas corretamente na pré-visualização.
- As regras funcionam em sequência sobre a mesma planilha (polimorfismo via `List<RegraTransformacao>`).

**Total: 33 pontos**

---

## Sprint 3 — Combinação, exportação e fichas iniciais (02/11 a 08/11)

| # | Issue | Labels | Est. | Prior. | RF |
|---|---|---|---|---|---|
| 23 | Implementar `ConsolidadorPlanilhas` (junção interna e à esquerda por chave) | planilhas, backend | 5 | Alta | RF04 |
| 24 | Tela para escolher planilhas e campo-chave da combinação | front | 3 | Alta | RF04 |
| 25 | Implementar `EscritorXlsx` e `EscritorCsv` e o `ExportacaoService` | planilhas, backend | 3 | Alta | RF08 |
| 26 | Pré-visualização do resultado e botão de download | front | 2 | Alta | RF07, RF08 |
| 27 | Registrar histórico das operações (`RegistroHistorico`) | backend, banco | 2 | Média | RF16 |
| 28 | Formulário de criação de ficha técnica com entrada manual | fichas, front | 3 | Alta | RF10 |
| 29 | `FichaService` e persistência de `FichaTecnica` e `CampoFicha` | fichas, backend, banco | 3 | Alta | RF10 |
| 30 | `ExtratorDocumentoPdf` (PDFBox) para um documento de exemplo | fichas, backend | 5 | Média | RF11 |
| 31 | Exibir origem e status de cada campo e destacar pendências | fichas, front | 3 | Alta | RF13 |
| 32 | Testes do consolidador, dos escritores e da `FichaTecnica` | teste | 3 | Alta | RNF05 |

**Critérios de aceite principais**
- Duas planilhas de exemplo combinadas por CPF ou placa geram o resultado esperado, inclusive com chaves sem correspondência.
- O arquivo exportado abre no Excel/LibreOffice com as colunas na ordem escolhida.
- Campo extraído do documento aparece como "Documento · A confirmar" até o usuário confirmar.

**Total: 32 pontos**

---

## Sprint 4 — Integrações, testes, interface final e demo (09/11 a 15/11)

| # | Issue | Labels | Est. | Prior. | RF |
|---|---|---|---|---|---|
| 33 | Revisão e confirmação manual dos campos da ficha | fichas, front | 3 | Alta | RF14 |
| 34 | Gerar ficha técnica em PDF (`GeradorFichaPdf`) | fichas, backend | 5 | Alta | RF15 |
| 35 | `FonteMockVeiculo` e interface pronta para API de placa autorizada | fichas, backend | 2 | Média | RF12 |
| 36 | Pesquisar APIs de consulta veicular disponíveis e registrar no SDD | fichas, docs | 1 | Baixa | RF12 |
| 37 | Ajustes de responsividade e mensagens de erro em todas as telas | front | 3 | Alta | RNF01 |
| 38 | Testes de integração dos fluxos principais | teste | 3 | Alta | RNF05 |
| 39 | Atualizar SDD para o MINT (RFs implementados, diferencial, IA: problemas e benefícios) | docs | 2 | Alta | — |
| 40 | README com instruções de execução e dados de exemplo | docs | 1 | Alta | RNF06 |
| 41 | Gravar vídeo de demonstração (até 2 min, sem áudio) | docs | 2 | Alta | — |
| 42 | Montar slides do MINT e fazer ensaio | docs | 2 | Alta | — |
| 43 | (Opcional) Salvar e reutilizar modelos de organização | planilhas | 5 | Baixa | RF09 |
| 44 | (Opcional) Sugestão de correspondência de colunas por similaridade de nome | ia, planilhas | 5 | Baixa | — |

**Critérios de aceite principais**
- Fluxo completo da demo funciona do zero em uma máquina limpa seguindo o README.
- PDF da ficha mostra só valores revisados e sinaliza campos não confirmados.
- Cobertura de testes do domínio ≥ 70%.

**Total: 24 pontos obrigatórios + 10 opcionais**
