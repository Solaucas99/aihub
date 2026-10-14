# 1. Observabilidade

**Tese:** em um sistema com vários modelos de linguagem, "funciona" não basta. Para virar case é preciso responder, com dados, a três perguntas: *quanto custou, quanto demorou e por que decidiu assim*. Hoje o Hub **não consegue responder nenhuma das três** sem abrir o banco à mão.

## Estado atual (verificado no código)

| Item | Situação |
|---|---|
| Spring Boot Actuator, Micrometer, OpenTelemetry | **Ausentes** (o `pom.xml` só tem Mongo, RestClient e WebMVC) |
| Contexto de log (MDC) com ids de conversa/execução/tarefa | **Ausente** |
| Uso de tokens e custo | **Descartado**: `OpenAiChatClient.complete` devolve só `choices[0].message`; o campo `usage` da resposta é ignorado |
| Latência por chamada de modelo | **Não medida** |
| Linha do tempo da execução | Parcial: `AgentRunLog` guarda `at`, `level`, `message`, `status`, `execution_status`, sem duração, sem tarefa, sem tipo de evento |
| Material bruto para depuração | **Bom**: `task_executions` guarda as mensagens e as chamadas de ferramenta de cada tentativa |
| Health check | Nenhum além de a aplicação responder |

O último ponto é uma vantagem: os dados para reconstruir o que aconteceu já existem; falta organizá-los e medi-los.

---

### OBS-1 · Medir tokens, custo e latência de cada chamada de modelo
**P0 · S–M · Alto**

- **Hoje:** o custo por pergunta é desconhecido. O pior caso é multiplicativo (veja [CST-1](06-custo-e-desempenho.md)) e ninguém o enxerga.
- **Proposta:**
  1. `OpenAiChatClient.complete` passa a devolver um `LlmResponse(message, usage{prompt,completion,total}, latencyMs, model, requestId)`.
  2. Cada chamador identifica o **papel** (`planner`, `subagent`, `validator`, `communicator`, `viz_converter`).
  3. Gravar em `task_executions.llm_calls[]` (por tentativa) e um agregado em `agent_runs.usage` (`tokens_in`, `tokens_out`, `calls`, `cost_estimated`, `duration_ms` por papel).
  4. Tabela de preços por modelo em configuração, para estimar o custo.
  5. Expor o agregado em `GET /conversations/{id}/agent-runs/{runId}`.
- **Pronto quando:** qualquer execução mostra tokens e custo estimado por papel, e a soma por papel bate com o painel do provedor dentro de 5%.

### OBS-2 · Logs estruturados com correlação
**P0 · S · Alto**

- **Hoje:** logs em texto com `conversationId=` e `agentRunId=` escritos à mão em algumas mensagens; sem MDC. Com threads virtuais e um pool por execução, as linhas de uma mesma pergunta se misturam.
- **Proposta:** colocar no MDC `conversation_id`, `message_id`, `run_id`, `task_id`, `subagent_id`, `attempt`, `role`; emitir JSON (ex.: encoder do Logstash); um `TaskDecorator`/wrapper de `Runnable` que **copia o MDC** para as threads do pool e para as threads virtuais.
- **Pronto quando:** filtrar por `run_id` no agregador de logs devolve a história completa de uma pergunta, em ordem.

### OBS-3 · Actuator e métricas (Micrometer)
**P0 · S · Alto**

Adicionar `spring-boot-starter-actuator` + registro Prometheus e publicar:

| Métrica | Tipo | Rótulos |
|---|---|---|
| `hub.message.accepted` | contador | — |
| `hub.planner.decision` | contador | `type=direct\|clarify\|delegate`, `context_action` |
| `hub.run.duration` | timer | `status` |
| `hub.run.active` | gauge | — |
| `hub.task.duration` | timer | `subagent`, `verdict` |
| `hub.validator.verdict` | contador | `verdict` |
| `hub.task.retry` | contador | `subagent` |
| `hub.hitl.pending` | gauge | — |
| `hub.llm.call.duration` | timer | `role`, `model`, `outcome` |
| `hub.llm.tokens` | contador | `role`, `model`, `direction=in\|out` |
| `hub.llm.error` | contador | `role`, `status` |
| `hub.provider.call.duration` | timer | `provider`, `tool`, `outcome` |
| `hub.artifact.publish.conflict` | contador | — |
| `hub.worker.lock.wait` | timer | — |

- **Pronto quando:** `/actuator/prometheus` expõe todas, protegido por rede/autenticação.

### OBS-4 · Rastreamento distribuído (OpenTelemetry)
**P1 · M · Alto**

Um trace por pergunta, com a hierarquia que já existe no código:

```
message.process
 ├─ planner.plan                 (atributos: decisão, contexto, nº de tarefas, prompt_version)
 ├─ decision.apply
 └─ run.execute
     ├─ task.execute [subagent, attempt]
     │   ├─ llm.call [role, model, tokens]
     │   ├─ provider.call [genie, poll_count]
     │   └─ validator.validate [verdict]
     └─ communicator.introduce
```

Exportar por OTLP para um coletor (Tempo/Jaeger). Propagar o contexto de trace pelas threads virtuais e pelo pool. Guardar o `trace_id` em `agent_runs` para abrir o trace a partir da tela de detalhes.
- **Pronto quando:** de um `run_id` chega-se ao trace e vê-se onde foram gastos os segundos (planejador, Genie, validador).

### OBS-5 · Linha do tempo da execução (API e tela)
**P1 · M · Alto**

- **Hoje:** `AgentRunLog` é uma lista de frases.
- **Proposta:** enriquecer o evento (`type`, `task_id`, `subagent_id`, `attempt`, `duration_ms`, `data`) e criar `GET …/agent-runs/{id}/timeline`. Na tela, um painel "Detalhes da execução" (sem prompts): etapas, tempo de cada uma, tentativas e vereditos.
- **Pronto quando:** uma pessoa de negócio entende, sem ajuda, por que uma resposta demorou 40 s ou precisou de 3 tentativas.

### OBS-6 · Painéis, SLOs e alertas
**P1 · M · Alto**

SLIs e metas iniciais (ajustar após o piloto):

| SLI | Meta sugerida |
|---|---|
| Tempo até a confirmação de recebimento (p95) | < 300 ms |
| Tempo de execução de resposta direta (p95) | < 8 s |
| Tempo de execução com delegação (p95) | < 90 s |
| Execuções `FAILED` | < 1 % |
| Tarefas aprovadas na 1ª tentativa | > 80 % |
| Taxa de HITL | acompanhar (sinal de perguntas ambíguas) |
| Execuções `RUNNING` há mais de 35 min | 0 |

Alertas: queda na taxa de aprovação, salto de `hub.llm.error{status=429}`, custo por pergunta acima do orçamento, execuções órfãs.

### OBS-7 · Registrar a decisão do planejador e o motivo
**P1 · S · Médio**

- **Hoje:** só o efeito da decisão (tarefas, contexto) é persistido; a decisão em si e o raciocínio se perdem.
- **Proposta:** gravar um registro por mensagem (`planner_decisions`) com a entrada resumida, a decisão, `prompt_version` e um campo curto **`reason`** acrescentado ao esquema estrito da resposta ("por que delegou/respondeu").
- **Pronto quando:** dá para auditar "por que ele não chamou o especialista?" semanas depois.

### OBS-8 · Versionar os prompts
**P1 · S · Médio**

- **Hoje:** os prompts são strings dentro de classes Java; uma mudança não deixa rastro na execução.
- **Proposta:** constante `PROMPT_VERSION` (ou hash do texto) por agente, gravada em `agent_runs`, `task_executions` e `planner_decisions`. Base para [QLD-3](03-qualidade-e-avaliacao-de-ia.md).

### OBS-9 · Health e readiness de verdade
**P0 · S · Médio**

`/actuator/health` com *liveness* (processo) e *readiness* (MongoDB acessível, `OPENAI_API_KEY` presente, autenticação da Genie obtida). Em caso de falha de dependência, o Hub deve sair do balanceador, não aceitar mensagens que não conseguirá processar.

---

**Ordem recomendada:** OBS-2 → OBS-1 → OBS-3 → OBS-9 (uma semana, destrava tudo) → OBS-5/7/8 → OBS-4 → OBS-6.

Próximo: [Extensibilidade](02-extensibilidade.md).
