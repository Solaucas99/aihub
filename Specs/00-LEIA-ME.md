# 00 — LEIA-ME: índice, regras de replicação e guia de uso das specs

Este conjunto de arquivos Markdown (`specs/`) descreve, **sem nada a mais e nada a menos**, o projeto **Hub de IA**: um backend Java 21 / Spring Boot 4.1.1 / MongoDB que orquestra um planejador, subagentes especialistas com tools (provider Genie), um validador e um comunicador, mais um frontend web estático e a suíte de testes. O objetivo é que um **LLM de código**, lendo somente estes arquivos numa máquina nova, recrie o mesmo projeto, com o mesmo comportamento.

## 1. Como usar (para o dono)

1. Copie a pasta `specs/` para a outra máquina, junto com os **dois PNG de `docs/`** (`hub-ia.png`, `mermaid-diagram.png`), que não cabem em Markdown (ver `12-docs-e-anexos.md`).
2. Crie um diretório vazio para o novo projeto e dê ao LLM de código **todos** os arquivos de `specs/` e o prompt da seção 2.
3. Deixe-o implementar na ordem da seção 5 e rodar as verificações de cada etapa.
4. No fim, `./mvnw test` deve dar **72 testes, 0 falhas, 5 ignorados** (ver `11-testes-e-criterios-de-aceite.md`).

## 2. Prompt sugerido para o LLM de código

> Você vai recriar, do zero e **exatamente**, o projeto descrito nos arquivos `specs/*.md`. Leia primeiro `00-LEIA-ME.md` e `01-visao-geral-e-arquitetura.md`, depois implemente seguindo a ordem do `00-LEIA-ME.md`. Regras: (1) crie **apenas** os arquivos da árvore de `02-projeto-build-e-infra.md`, nada mais; (2) todo bloco marcado como **literal** deve ser copiado **caractere por caractere**; (3) onde a spec descreve comportamento, implemente exatamente esse comportamento, incluindo mensagens, constantes, ordens de operação e casos de borda; (4) não adicione dependências, funcionalidades, comentários, testes ou arquivos que as specs não pedem; (5) não "melhore" nem "corrija" nada: as limitações conhecidas estão registradas nas specs de propósito; (6) ao terminar cada etapa, rode a verificação indicada e só avance se passar; (7) se algo estiver ambíguo, **pare e pergunte** em vez de adivinhar.

## 3. Regras de replicação (valem para todas as specs)

1. **Literal vence prosa.** Blocos de código marcados `Arquivo ... (literal)` ou `Trecho ... (literal)` são o conteúdo exato do arquivo/trecho original. Se a explicação em prosa divergir de um bloco literal, o bloco literal está certo.
2. **Arquivo inteiro literal**: quando a spec diz "Arquivo `caminho` (literal)", o arquivo criado deve ficar **idêntico** ao bloco. **143 dos 148 arquivos do projeto aparecem inteiros nas specs**; as únicas exceções são `mvnw` e `mvnw.cmd` (gerados pelo Maven Wrapper: regenerar conforme `02`), os 2 PNG de `docs/` (binários: copiar à parte) e `logs/.gitkeep` (arquivo vazio). A tabela por arquivo está em `99-matriz-de-cobertura.md`. A prosa de cada spec explica o **porquê** e o comportamento; o bloco literal é a fonte exata.
3. **Indentação com TAB** em todo o código Java e no JavaScript/CSS dos blocos literais (preserve exatamente tabs e espaços do bloco). YAML, JSON e XML seguem o que o bloco mostra.
4. **Textos em português sem acentos** (prompts, mensagens ao usuário, logs) são intencionais; não acentue nem traduza.
5. **Nomes**: pacote base `com.lamorim.hubdeia`; `groupId` `com.lamorim`; `artifactId` `hubdeia`. Campos Mongo/JSON em `snake_case`; Java em `camelCase`.
6. **Java 21**: o código usa *records*, `List.getLast()`, `List.reversed()`, threads virtuais, `switch` com setas e *pattern matching*.
7. **Nada de Redis, nada de outro banco, nada de segredos no código.**
8. **Sem extras**: não crie `README` raiz, CI, Dockerfile, `.env.example`, `application-*.yaml`, scripts, nem nada que não esteja na árvore.
9. **Quebras de linha**: UTF-8, LF (o `.gitattributes` fixa LF em `mvnw` e CRLF em `*.cmd`).

## 4. Índice das specs

| Arquivo | Conteúdo | Código coberto |
|---|---|---|
| `01-visao-geral-e-arquitetura.md` | O que é, princípios, camadas, glossário, fluxos, limites | transversal |
| `02-projeto-build-e-infra.md` | **Árvore exata de arquivos**, `pom.xml`, `application.yaml`, Docker, `.gitignore`, wrapper, Dotenv, CORS, REST client | `pom.xml`, `config/*` (exceto Bootstrap), infra |
| `03-dominio-e-mongodb.md` | Todos os *records*/enums/repositórios literais, coleções e índices | `domain/**` |
| `04-catalogo-e-bootstrap.md` | Catálogo (WorkContext, Subagent, Skill, Tool, Provider), JSONs semente, bootstrap, política de acesso | `bootstrap/*.json`, `BootstrapConfiguration`, `AgentSkillAccessPolicy` |
| `05-api-rest.md` | Endpoints, DTOs, autenticação, erros, CORS | `api/**`, `HelloController` |
| `06-conversa-e-planejador.md` | Conversas, worker, planejador (**prompt e schema literais**), aplicação da decisão | `ConversationService`, `ConversationWorker`, `planner/**`, etc. |
| `07-orquestracao-de-runs.md` | Ciclo de vida da run, orquestração paralela, dependências, validador e comunicador (**prompts literais**) | `run/**`, `AgentRunOrchestrator`, `validation/**`, `communication/**` |
| `08-execucao-do-subagente-e-conteudo.md` | Laço do subagente, prompts, cliente OpenAI, `TaskOutput`, artefatos, contrato de tools | `SubagentTaskExecutor`, `SubagentPromptBuilder`, `llm/**`, `content/**`, `Artifact*`, `provider/*` |
| `09-provider-genie.md` | Provider Genie completo | `provider/genie/**` |
| `10-frontend.md` | Interface web (**arquivos literais**) | `frontend/**` |
| `11-testes-e-criterios-de-aceite.md` | Testes (**arquivos literais**) e critérios de aceite | `src/test/**` |
| `12-docs-e-anexos.md` | Docs literais e anexos fora de escopo | `docs/**` |
| `99-matriz-de-cobertura.md` | Para cada arquivo do projeto, em qual spec está | todos |

## 5. Ordem de implementação e verificação

| Etapa | Specs | Verificação |
|---|---|---|
| 1. Projeto e infra | `02` | `docker compose up -d`; `./mvnw -q -DskipTests compile` (depois das demais) |
| 2. Domínio Mongo | `03` | compila; índices criados na subida |
| 3. Catálogo | `04` | semente cria 1/1/4/2/1 registros |
| 4. Contrato de tools, conteúdo e artefatos | `08` (parte 1) + `WorkContextService` e `ConversationMemorySanitizer` (de `06`) + `TaskExecutionResult` (de `07`), que o `ArtifactService` exige | compila; `TaskOutputTest`, `ArtifactServiceTest` |
| 5. Conversa, planejador, runs e executor (**compilam juntos**: há dependência circular entre os pacotes `planner` e `run`) | `06`, `07`, resto de `08` e **os 8 DTOs de `05`** (usados por `ConversationService` e `AgentRunService`) | `ConversationServiceTest`, `PlannerDecisionHandlerTest`, `OpenAiPlannerClientTest`, `ConversationPlannerAgentTest`, `AgentRunStoreTest`, `AgentRunOrchestratorTest`, `TaskDependencyResolverTest`, `SubagentMemoryTest` |
| 6. Provider Genie | `09` | compila; verificação manual contra o Genie real |
| 7. API (controllers) | `05` (`AuthHeaders`, `ConversationController`, `HelloController`) | `GET /hello`; fluxo de curl da spec 05 |
| 8. Frontend | `10` | abrir `frontend/index.html` e conversar |
| 9. Testes finais e docs | `11`, `12` | `./mvnw test` → 72 testes, 0 falhas, 5 ignorados |

## 6. Escopo: o que está dentro e fora

**Dentro** (a árvore completa está em `02-projeto-build-e-infra.md`): `pom.xml`, wrapper Maven, `docker-compose.yaml`, `.gitignore`, `.gitattributes`, `logs/.gitkeep`, `application.yaml`, `spring.factories`, todo o `src/main/java`, os JSONs de bootstrap, `frontend/*`, `src/test/**` (inclui um fixture JSON), `docs/data-model.md` e `docs/implementation-summary.md`.

**Fora**: `js-hub/` (implementação Node.js legada), `scripts/seed-subagents.js` (legado), `docs/*.png` (copiar à parte), e arquivos locais (`.idea/`, `.vscode/`, `.codex/`, `HELP.md`, `payload_genie*.json`, `target/`, `logs/*.log`, `.env`).

## 7. Convenções de leitura

- "**Depende de**" no topo de cada spec lista as specs necessárias.
- "**Armadilhas e decisões não óbvias**" registra o *porquê* e os gotchas (propriedades do Spring Boot 4, mapeamento Mongo, concorrência).
- "**Critérios de aceite**" diz como provar que a parte está correta (normalmente testes da spec 11).
- Os trechos literais foram **gerados mecanicamente** a partir do código-fonte original; não contêm erros de transcrição.

## 8. Limitações conhecidas (replicar como estão)

Autenticação por id em cabeçalho; sem retomada de runs órfãs; sem SSE; sem avaliação automatizada fora do teste live opt-in; planejador sem informação de work context por run (relevante só com vários contextos); Genie é o único provider; sem API administrativa do catálogo. Detalhes em `01-visao-geral-e-arquitetura.md`.
