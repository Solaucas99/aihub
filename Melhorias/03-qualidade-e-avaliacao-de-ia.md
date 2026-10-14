# 3. Qualidade e avaliação de IA

**Tese:** o que mais convence em um case de IA é **evidência de qualidade repetível**, não uma demo bem-sucedida. O Hub já tem a base certa (72 testes unitários e um teste "vivo" que reproduz um erro real do planejador). Falta transformar isso em um **processo de avaliação contínuo**.

## Estado atual (verificado no código)

- 15 arquivos de teste (72 testes); os de integração exigem MongoDB local (`localhost:27017`, banco `appdb_test`).
- `PlannerLiveEvalTest` roda **contra o modelo real**, mas só com `-Dplanner.live=true` (são os 5 testes "ignorados" na suíte padrão) e com **um** cenário de fixture (`page-view-conversation.json`).
- O validador (LLM-juiz) **não tem avaliação própria**: ninguém mediu se ele acerta CONTINUE/RETRY/HITL/CANCEL.
- Não há coleta de feedback do usuário.

---

### QLD-1 · Conjunto de avaliação (evals) do planejador
**P0 · M · Alto**

- **Proposta:** um conjunto "dourado" de 50–100 conversas em JSON (no formato da fixture atual), cada uma com o resultado esperado:
  - rota: `direct | clarify | delegate`;
  - especialista esperado (inclusive o **retorno ao mesmo especialista** em pedidos como "agora em gráfico");
  - ação de contexto (`NONE/KEEP/ACTIVATE`);
  - comportamento com artefato vigente, artefato desatualizado e "volta ao anterior".
- **Métricas:** acurácia de rota, acurácia de especialista, taxa de "planejador fez o trabalho do especialista" (deve ser 0), estabilidade em 5 repetições.
- **Pronto quando:** `./mvnw test -Dplanner.live=true -Dsuite=planner` imprime um relatório e **falha se a acurácia cair abaixo do limite** acordado.

### QLD-2 · Calibrar o validador (juiz)
**P1 · M · Alto**

- **Proposta:** casos rotulados por humanos (resposta boa, vazia, irrelevante, pedindo esclarecimento, falha do provider) e medição de concordância. Atenção especial à regra "HITL tem prioridade": uma resposta que só pergunta nunca pode virar CONTINUE.
- **Pronto quando:** existe uma matriz de confusão do validador e uma meta (por exemplo, ≥ 90 % de concordância com revisão humana).

### QLD-3 · Gate de regressão de prompts no CI
**P1 · S · Alto**

Toda mudança em prompt ([OBS-8](01-observabilidade.md)) roda QLD-1 e QLD-2 e comenta o diff de métricas no pull request. Execução noturna agendada para detectar mudança de comportamento do **modelo** (não do código).

### QLD-4 · Provedores falsos e testes ponta a ponta determinísticos
**P1 · M · Médio**

Um servidor falso da OpenAI e da Genie (WireMock ou equivalente) com roteiros gravados permite testar o fluxo completo (planejar → executar → validar → comunicar) sem custo e sem variação. Também serve para a demonstração offline.

### QLD-5 · Testes de falha e caos
**P1 · M · Médio**

Cenários: OpenAI com 429/500/timeout; Genie lenta; MongoDB lento; processo morto no meio de uma execução ([CNF-1](05-confiabilidade-e-escala.md)); resposta do modelo fora do esquema. Cada um com resultado esperado documentado.

### QLD-6 · Feedback humano alimentando o conjunto dourado
**P2 · M · Alto**

Polegar para cima/baixo com motivo ([PRD-2](07-produto-e-experiencia.md)) cria uma fila de revisão; respostas ruins confirmadas viram casos novos de QLD-1/2. É o ciclo que faz a qualidade subir com o uso.

### QLD-7 · Evidência e rastreabilidade nas respostas
**P1 · M · Alto**

- **Hoje:** o usuário recebe a análise, mas não vê **de onde vieram os números**.
- **Proposta:** o artefato guarda a **fonte e a consulta** usadas (por exemplo, o texto SQL que a Genie executou, o espaço de dados e a data/hora), exibidas em "Como cheguei nisso" ([PRD-1](07-produto-e-experiencia.md)). O validador passa a checar se a resposta cita uma consulta executada.
- **Pronto quando:** toda resposta com números traz a fonte; resposta com números sem consulta registrada é reprovada.

---

## Métricas de qualidade para o painel

| Métrica | Como medir |
|---|---|
| Aprovação na 1ª tentativa | tarefas `CONTINUE` na tentativa 1 ÷ tarefas |
| Taxa de RETRY / cancelamento por tentativas | validador + orquestrador |
| Taxa de HITL e de resposta ao HITL | pedidos criados × respondidos |
| Satisfação | polegar para cima ÷ avaliações |
| "Alucinação percebida" | respostas com números sem consulta registrada |
| Acurácia de rota do planejador | QLD-1 |

Próximo: [Segurança e governança](04-seguranca-e-governanca.md).
