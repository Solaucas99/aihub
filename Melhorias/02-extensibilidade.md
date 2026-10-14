# 2. Extensibilidade do Hub

**Tese:** o maior diferencial do desenho atual é que **o catálogo é dado**. O case de sucesso é provar isso: *"um agente novo entra em produção sem uma linha de código"*. Falta transformar essa promessa em algo seguro, validado e demonstrável.

## Estado atual (verificado no código)

| Pergunta | Hoje |
|---|---|
| Criar um especialista que usa a Genie | Possível só com documentos no MongoDB (skill, subagente, contexto). Sem tela, sem API, sem validação. |
| Quem valida o catálogo? | Ninguém: um `skill_id` inexistente ou um ciclo produz/consome só aparece em tempo de execução. O bootstrap só grava em coleções vazias. |
| Nova fonte de dados | Uma classe que implementa `ProviderTool` (`name`, `schema`, `execute`), encontrada pelo `ProviderToolRegistry` pelo nome. Só a Genie foi exercitada. |
| Outro modelo além da OpenAI | `OpenAiChatClient` é uma classe concreta e as mensagens trafegam como `Document` do Mongo; há `model` e `subagent-model`, sem escolha por especialista. |
| Planejador com vários contextos | Limite conhecido: não vê qual contexto cada execução usou. |

---

### EXT-8 · Prova de extensibilidade: o agente "Briefing" e um teste de aceitação
**P0 · S · Alto — comece por aqui**

- **Proposta:** criar de fato o especialista de Briefing (`produces: ["BRIEFING"]`, `consumes: ["INSIGHT"]`) **só com documentos**, e escrever um teste que carrega um catálogo de fixture com 3 especialistas e verifica: o planejador enxerga o novo, a ordem de execução respeita produz/consome, e a política de acesso entrega as ferramentas certas. Documentar o passo a passo com um script de importação.
- **Pronto quando:** o teste prova "novo agente = zero código" e fica no CI para sempre. É também o roteiro da demonstração ([case de sucesso](09-case-de-sucesso.md)).

### EXT-1 · API e administração do catálogo, com validação
**P1 · M · Alto**

- **Proposta:** endpoints `…/admin/{work-contexts|subagents|skills|tools|providers}` protegidos por papel, com validações antes de gravar:
  - referências existem (skill → tool → provider; contexto → especialistas);
  - o especialista tem o provider que a ferramenta exige;
  - sem ciclo em produz/consome (hoje o resolvedor degrada para paralelo, o que esconde o erro);
  - `description` e `system_prompt` não vazios (o planejador depende da descrição).
  Modo **rascunho → publicar**, com pré-visualização "o planejador enxergaria isto".
- **Pronto quando:** uma pessoa não técnica cria um especialista pela tela e o Hub rejeita configurações quebradas com mensagem clara.

### EXT-2 · Versão do catálogo por execução
**P2 · S · Médio**

Gravar `catalog_version` (ou hash dos documentos usados) em cada `agent_run`. Torna reproduzível "por que essa execução se comportou assim" mesmo depois de editar um especialista.

### EXT-3 · Provider MCP (Model Context Protocol)
**P1 · M · Alto**

- **Ideia:** em vez de escrever uma classe Java por fonte, implementar **um** `ProviderTool` que fala MCP e expõe as ferramentas de qualquer servidor MCP (bancos, CRMs, buscadores) como ferramentas do catálogo. O contrato atual (`name`, `schema`, `execute`) já comporta isso.
- **Pronto quando:** um servidor MCP de terceiros é plugado só com configuração e um especialista passa a usá-lo.

### EXT-4 · Ferramentas HTTP declarativas
**P2 · M · Alto**

Descrever no catálogo uma ferramenta REST (URL, método, esquema dos argumentos, template de corpo, referência a segredo, mapeamento da resposta). Cobre a maioria das integrações sem Java. Guardrails: somente hosts permitidos, timeout e tamanho máximo de resposta.

### EXT-5 · Abstração de modelo de linguagem
**P1 · M · Alto**

- **Hoje:** acoplamento à OpenAI e ao `Document`.
- **Proposta:** interface `LlmClient` com tipos próprios (mensagens, ferramentas, resposta com `usage`), implementações para OpenAI/Azure OpenAI/outros, **modelo por papel e por especialista** (`subagents.model`) e **fallback** (modelo secundário quando o primário falha ou está limitado). Pré-requisito para [OBS-1](01-observabilidade.md) e [CST-2](06-custo-e-desempenho.md).

### EXT-11 · Planejador ciente do contexto de cada execução
**P1 · S · Médio**

Limite conhecido. Incluir, em `recent_runs`, o `work_context_id` usado. Sem isso, com dois ou mais contextos o planejador pode errar entre `KEEP` e `ACTIVATE`. Adicionar ao conjunto de avaliação ([QLD-1](03-qualidade-e-avaliacao-de-ia.md)) um caso de troca de contexto.

### EXT-6 · Planejamento em mais de uma passada
**P2 · L · Médio**

Hoje o plano é decidido uma vez, antes de ver resultados. Evolução: **replanejar** depois do primeiro resultado ("a análise mostrou X, agora preciso de Y"), tarefas de vários passos para um mesmo especialista e dependências opcionais explícitas. Só vale depois de os evals estarem sólidos: aumenta autonomia, e portanto risco e custo.

### EXT-7 · Renderizadores e exportadores plugáveis
**P2 · M · Médio**

O conteúdo visual já é neutro (HTML, Vega-Lite). Acrescentar formatos (por exemplo `TABLE_JSON`, `MARKDOWN`, `IMAGE`) e exportadores (PDF, CSV, PPT) que consomem o mesmo `Visual`, sem tocar em providers.

### EXT-9 · Eventos de saída e novos canais
**P2 · M · Médio**

Webhooks (`run.finished`, `hitl.requested`) e adaptadores de entrada (Slack/Teams). O fluxo já é assíncrono por desenho, então um canal novo é uma casca fina sobre a API.

### EXT-10 · Multi-tenant
**P2 · L · Alto**

Catálogo e dados por organização: `tenant_id` em todas as coleções, índices por tenant, cotas. Decidir cedo, porque retrofit é caro. Depende de [SEG-1/2](04-seguranca-e-governanca.md).

### EXT-12 · Base de conhecimento (RAG) como provider
**P2 · L · Alto**

Um provider de documentos (busca vetorial, por exemplo Atlas Vector Search) permitiria especialistas que respondem com políticas, playbooks e briefings anteriores, citando a fonte.

---

**Ordem recomendada:** EXT-8 → EXT-5 → EXT-1 → EXT-11 → EXT-3 → (EXT-2, EXT-4) → restante conforme demanda.

Próximo: [Qualidade e avaliação de IA](03-qualidade-e-avaliacao-de-ia.md).
