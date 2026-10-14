# 5. Confiabilidade e escala

**Tese:** o desenho concorrente do Hub é cuidadoso (atualizações condicionais, execução que termina uma só vez, índices únicos). O que falta é o que acontece **quando algo fora do controle falha** e **quando há mais de uma instância ou mais de um usuário ao mesmo tempo**.

## Estado atual (verificado no código)

| Tema | Situação |
|---|---|
| Falha de OpenAI (429/5xx) | `OpenAiChatClient.complete` faz uma chamada com *timeout* de 60 s; **sem retry, sem backoff, sem respeito a `Retry-After`**. Um 429 vira falha de tarefa (e RETRY do validador gasta outra tentativa inteira). |
| Execução interrompida | Limite conhecido: se o processo cair, a execução fica `RUNNING` para sempre. |
| Fila de mensagens | `ConversationWorker.dispatch` usa um executor **em memória**: se o processo cair depois do "aceito", a mensagem nunca é planejada. |
| Mais de uma instância | As 64 travas por conversa são `ReentrantLock` **locais**: só garantem ordem em **uma** instância. |
| Concorrência global | Cada execução cria seu próprio pool (`min(4, tarefas)`), cada mensagem vira uma thread virtual: **não há teto global**. O pool de tarefas usa threads de plataforma que **bloqueiam** na Genie (`Thread.sleep` a cada 8 s, até 30 min). |
| Reenvio de mensagem | Sem idempotência: um clique duplo gera duas execuções. |
| Cancelar | Não existe endpoint para o usuário cancelar uma execução. |
| Andamento | Polling a cada 2,2 s; a tela recarrega 700 ms depois do status final para cobrir uma corrida conhecida. |
| Banco | Nó único de desenvolvimento; sem TTL; `task_executions` cresce com mensagens brutas. |

---

### CNF-1 · Recuperar execuções órfãs
**P0 · S–M · Alto**

- **Proposta:** `heartbeat_at` atualizado enquanto a execução roda; um *reaper* periódico (e na subida) marca como `FAILED`, com mensagem clara ao usuário, as execuções `RUNNING` sem batimento há mais de N minutos. Segunda fase: **retomar** tarefas idempotentes em vez de falhar.
- **Pronto quando:** matar o processo no meio de uma execução resulta, após o prazo, em `FAILED` e mensagem ao usuário; teste de caos cobrindo o caso ([QLD-5](03-qualidade-e-avaliacao-de-ia.md)).

### CNF-2 · Retry, backoff e disjuntor para OpenAI e Genie
**P0 · S–M · Alto**

- **Proposta:** política única por provedor: retry apenas para erros transitórios (429, 5xx, *timeout*), backoff exponencial com *jitter*, respeito a `Retry-After`, máximo de 3 tentativas; **disjuntor** (Resilience4j) que abre após falhas consecutivas e falha rápido; **modelo de fallback** ([EXT-5](02-extensibilidade.md)). Erros de cliente (400) **não** são repetidos.
- **Pronto quando:** simulando 429 intermitente, a execução conclui sem consumir tentativas do validador; simulando indisponibilidade prolongada, a resposta ao usuário é rápida e clara.

### CNF-3 · Contrapressão e limites globais de concorrência
**P1 · M · Alto**

- **Proposta:** semáforos globais **por provedor** (por exemplo N consultas simultâneas à Genie, M chamadas simultâneas à OpenAI), limite de execuções ativas por usuário, e pool de tarefas em **threads virtuais** (o bloqueio na Genie deixa de custar uma thread de plataforma). Fila com posição/estimativa quando saturado.
- **Pronto quando:** um teste de carga com 100 conversas simultâneas não esgota threads nem estoura a cota do provedor, e o excesso espera em fila em vez de falhar.

### CNF-4 · Fila durável e escala horizontal
**P2 · L · Alto**

- **Proposta:** gravar a mensagem com estado `PENDING` e processá-la por um consumidor que reivindica trabalho (arrendamento no próprio MongoDB com `findAndModify`, ou uma fila). Substituir as travas locais por **arrendamento por conversa** (ou particionamento por `conversation_id`), para que 2+ instâncias não planejem a mesma conversa ao mesmo tempo.
- **Enquanto isso:** documentar explicitamente "**instância única**" como restrição de implantação.
- **Pronto quando:** derrubar uma instância não perde mensagens e duas instâncias respeitam a ordem por conversa.

### CNF-5 · Idempotência no envio
**P1 · S · Médio**

Cabeçalho `Idempotency-Key` (ou `client_message_id` gerado pela tela) com índice único `(conversation_id, client_message_id)`; reenvio devolve a mesma resposta de aceitação.

### CNF-6 · Tempo real (SSE)
**P2 · M · Médio**

`GET …/events` (Server-Sent Events) emitindo `progress`, `task_finished`, `run_finished`. Remove o polling, a corrida de 700 ms e permite mostrar etapas em tempo real ([PRD-1](07-produto-e-experiencia.md)). O polling permanece como alternativa.

### CNF-7 · Cancelamento pelo usuário e prazo máximo por execução
**P1 · M · Alto**

- Endpoint `POST …/agent-runs/{id}/cancel` que usa a mesma transição condicional `RUNNING → CANCELLED` já existente e sinaliza cancelamento cooperativo às tarefas em andamento (a espera da Genie também deve ser interrompida).
- **Prazo máximo** configurável por execução (por exemplo 10 min) após o qual ela termina com mensagem clara. Hoje o limite efetivo é o `poll-timeout` da Genie de 30 min por consulta.

### CNF-8 · Retenção, arquivamento e crescimento do banco
**P1 · S · Médio**

TTL e arquivamento ([SEG-6](04-seguranca-e-governanca.md)); revisar tamanho do documento `agent_runs` (tarefas e logs embutidos); paginar listagens; monitorar índices e tamanho das coleções.

### CNF-9 · Backup, réplica e recuperação
**P1 · S · Alto**

*Replica set* (necessário também para transações e *change streams*), backup agendado com **teste de restauração**, objetivos de recuperação (RPO/RTO) escritos e verificados.

---

**Ordem recomendada:** CNF-1 e CNF-2 (confiança básica) → CNF-5, CNF-7 → CNF-3 → CNF-8/9 → CNF-6 → CNF-4 (só quando houver necessidade real de mais de uma instância).

Próximo: [Custo e desempenho](06-custo-e-desempenho.md).
