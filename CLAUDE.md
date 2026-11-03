# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Imperium Motors: academic OOP project (Java 21 + Spring Boot 3.4, Thymeleaf, JPA, Flyway). It combines spreadsheet cleanup/consolidation, vehicle technical sheets ("fichas técnicas") with per-field provenance, and a used-car store (public site + staff panel). **All code, identifiers, UI text, commit-facing docs and comments are in Brazilian Portuguese** — keep new code consistent with that.

Design decisions (ADRs D01–D16), requirements and diagrams live in `docs/SDD.md`; the sprint backlog is `docs/ISSUES.md`. When a change alters architecture, requirements or test counts, those docs (and the README) are expected to be updated too.

## Commands

Use `mvnw.cmd` on Windows, `./mvnw` elsewhere.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # no DB install: H2 file DB in ./dados (VS Code default build task)
docker compose up -d banco && ./mvnw spring-boot:run      # default profile: PostgreSQL 16 on localhost:5432
docker compose --profile app up --build                    # app + DB in containers

./mvnw test                                                # all tests (H2 in-memory, see src/test/resources/application.properties)
./mvnw verify -Ppostgres                                   # same tests on real PostgreSQL 16 via Testcontainers (needs Docker; CI runs both)
./mvnw test -Dtest=RegrasTransformacaoTest                 # one class
./mvnw test -Dtest=LojaIntegracaoTest#nomeDoMetodo         # one method
./mvnw package -DskipTests
```

- App at http://localhost:8080. `/` is the public store; staff area `/painel` (login `admin` / `dataflow123`, overridable via `DATAFLOW_ADMIN_USUARIO` / `DATAFLOW_ADMIN_SENHA`). The panel has a "Carregar carros de demonstração" button (`DemonstracaoService`).
- The `local` profile serves templates and static files straight from `src/main/resources` (`file:` prefix), so template/CSS/JS edits show on browser refresh without restarting.
- Uploaded files go to `./dados/arquivos`, photos to `./dados/fotos` (gitignored, outside public static path).
- Demo inputs are in `exemplos/` (plates `ABC1D23`, `BRA2E19`, `XYZ9876` exist in the demo vehicle source); `excel/` holds the expense-control spreadsheet and the Python script that generates it.

## Architecture

Modular monolith under `br.com.dataflowauto`, one package per module, each split into the same layers:

- `dominio` — pure business objects, no Spring/JPA-library knowledge beyond entity mapping; depends only on interfaces.
- `aplicacao` — Spring services orchestrating domain + repositories; record history via `HistoricoService`.
- `infra` — interface implementations (POI, Commons CSV, PDFBox, OpenPDF, HTTP API) and Spring Data repositories.
- `web` — controllers, form objects, Thymeleaf views (`src/main/resources/templates/<modulo>/`).

Modules: `comum` (file storage/validation, history, error handling `TratadorErros`, security `SegurancaConfig`, store info `DadosLoja`), `planilhas`, `fichas`, `vendas` (store, stock, expenses, leads/"propostas", sales, dashboard), `marketing` (per-channel ad texts, photo ZIP, tracking where each car is published; flags active publications of sold/removed cars).

Cross-module rules worth knowing before editing:

- **Spreadsheets are immutable and kept in session.** `Planilha`/`Linha`/`Coluna` are immutable; each step (`RegraTransformacao.aplicar` → Template Method `validar` + `transformar`, or `ConsolidadorPlanilhas.combinar`) returns a new `Planilha`. The session-scoped `AreaTrabalho` keeps only the starting and current `Planilha` plus each step as a replayable `AreaTrabalho.Etapa` (Command); undo replays the remaining steps from the start, so step operations must be pure (use `PlanilhaService.aplicarRegras`/`juntar`, not the history-logging `organizar`/`combinar`). Every workspace form/link carries the `area` id, checked with `AreaTrabalho.conferir`, so a stale browser tab can't edit/export another tab's sheet. Sheets are capped at `Planilha.MAXIMO_LINHAS` (readers and joins). Row data is never stored in the DB — only file metadata (`ArquivoImportado`). New transformations = new `RegraTransformacao` subclass; new standardizations = new `TipoPadronizacao` enum constant carrying its function. Readers/writers are picked by format via `LeitorPlanilha`/`EscritorPlanilha` beans.
- **Ficha provenance invariant.** Every `CampoFicha` has an `OrigemDado` (USUARIO, DOCUMENTO, FONTE_EXTERNA) and `StatusVerificacao`. Anything not typed by the user starts as `A_CONFIRMAR`; never mark external/document data as confirmed automatically. `FichaService` applies user input first, then queries `FonteDadosVeiculo` beans in `@Order` (FonteDocumento → FonteApiPlaca, active only if `API_PLACA_URL` set → FonteDemonstracao); the first source providing a field wins. A ficha can only be concluded when required fields are confirmed; PDFs of unfinished fichas are watermarked as draft.
- **Store depends on fichas, not the reverse.** An `Anuncio` can only be created from a concluded ficha and reads make/model/etc. from it; if the ficha goes back to review, the car leaves the store. `fichas` asks "is this ficha in use?" through the `VerificadorUsoFicha` interface, implemented in `vendas` by `UsoFichaPelaLoja` (ADR D14). Don't make `fichas` import from `vendas`; use an interface owned by `fichas` instead.
- **Public pages show only confirmed fields** and never full plate, chassis, costs or customer data.
- Expense import (`DespesaService`) reuses the `planilhas` readers and links rows to cars by plate, storing origin "Planilha: arquivo, linha N".
- Money is `BigDecimal` (`Dinheiro`); financing simulation uses the Price table (`SimulacaoFinanciamento`).
- Uploaded files are validated by extension, size **and magic bytes** (macros like `.xlsm` rejected); photos (JPG/PNG/WEBP) are saved with UUID names and names are validated before opening. CSV export uses `;`, UTF-8 with BOM, and prefixes `= + - @` with an apostrophe.

### Security / front-end constraints

- Public routes: `/`, `/loja/**`, `/fotos/**`, static assets, `/entrar`. Everything else requires login.
- CSRF is on for all forms, including the public contact form — Thymeleaf forms must include the token; MockMvc tests use `.with(csrf())` and `.with(user(...))`.
- CSP is `script-src 'self'; style-src 'self'`: **no inline `<script>`, inline event handlers, or `style` attributes/blocks** in templates. Put JS in `static/js/` and CSS in `static/css/`. Pages are expected to work without JavaScript.

### Database

Schema is owned by Flyway (`src/main/resources/db/migration/V{n}__descricao.sql`) and Hibernate runs with `ddl-auto=validate`, so every entity change needs a new migration. Migrations must run on both PostgreSQL and H2 in `MODE=PostgreSQL` (tests and `local` profile). Never edit an existing migration.

## Tests

JUnit 5 + AssertJ. Domain unit tests per module under `src/test/java/br/com/dataflowauto/<modulo>/`; end-to-end web flows via `@SpringBootTest` + MockMvc on H2 (`FluxosIntegracaoTest`, `LojaIntegracaoTest`, `DivulgacaoIntegracaoTest`). Test config uses a 1 MB upload limit and temp-dir storage.
