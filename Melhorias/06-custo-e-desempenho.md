# 6. Custo e desempenho

**Tese:** o desenho "planejar, executar, validar, comunicar" troca custo por confiabilidade. Isso é uma boa troca, **desde que o custo seja visível e limitado**. Hoje ele não é nenhum dos dois.

## Por que o custo precisa de atenção: o pior caso

Valores de configuração atuais: até **3 tentativas** por tarefa (`TASK_RETRY_ATTEMPTS`), até **8 rodadas** de ferramenta por tentativa (`OPENAI_MAX_TOOL_ROUNDS`), até **4 tarefas** em paralelo.

Para uma execução com *T* tarefas, o teto teórico de chamadas de modelo é aproximadamente:

```
1 (planejador)
+ T × 3 tentativas × (até 8 rodadas do especialista + 1 do validador)
+ 1 (comunicador)
+ conversões de gráfico (1 por visual da Genie)
```

Com T = 2: até **~56 chamadas** em um único pedido. O caso típico é bem menor (uma tentativa, poucas rodadas), mas **sem [OBS-1](01-observabilidade.md) ninguém sabe a distribuição real**, e um especialista em laço ou um validador exigente pode multiplicar o gasto sem aviso.

## Estado atual (verificado no código)

- `model` e `subagent-model` configuráveis; `viz-conversion-model` separado. Não há modelo por papel nem por especialista.
- O planejador recebe as últimas **10 mensagens** (`HISTORY_SIZE`) mais resumos truncados de execuções e artefatos; não há resumo de conversa longa.
- Tudo que não é incluído nessas janelas é esquecido pelo planejador; resultados voltam a ser consultados na fonte, que é lenta e cara.
- Sem cache de consultas à Genie (o artefato vigente funciona como cache lógico quando o planejador decide reaproveitá-lo).

---

### CST-1 · Orçamento de chamadas por execução
**P1 · S · Alto**

- **Proposta:** limites configuráveis (`max_llm_calls_per_run`, `max_tokens_per_run`) verificados antes de cada chamada; ao estourar, a execução termina de forma controlada ("atingi o limite de consultas, aqui está o que consegui") em vez de seguir gastando. Alertar quando o p95 de chamadas por execução subir.
- **Pronto quando:** nenhuma execução ultrapassa o orçamento e o motivo aparece na linha do tempo.

### CST-2 · Modelo por papel
**P1 · S · Alto**

Hoje a maior parte dos papéis usa o mesmo modelo. Sugestão de partida (medir antes de fixar com [QLD-1/2](03-qualidade-e-avaliacao-de-ia.md)): modelo forte para planejador e especialistas; modelo menor e mais barato para **validador, comunicador e conversão de gráficos**. Configurável por papel e, depois, por especialista ([EXT-5](02-extensibilidade.md)). A decisão deve ser tomada com os evals, não por intuição.

### CST-3 · Validador com pré-checagens determinísticas
**P1 · S · Médio**

Antes de chamar o juiz (LLM): saída vazia, erro de provider ou resposta sem nenhuma consulta executada → `RETRY` direto, sem custo e com resultado previsível. Reserva o juiz para os casos que exigem julgamento. Reduz custo, latência e variação.

### CST-4 · Memória longa com resumo rolante
**P2 · M · Médio**

Para conversas longas, manter um resumo atualizado (decisões, filtros, períodos, artefatos relevantes) além da janela de 10 mensagens, em vez de aumentar a janela. Precisa de avaliação: resumo ruim é pior que janela curta.

### CST-5 · Cache de consultas às fontes
**P2 · M · Médio**

Chave `(especialista, pergunta normalizada, filtros, espaço de dados)` com TTL curto e indicação de "dados de hh:mm" na resposta ([QLD-7](03-qualidade-e-avaliacao-de-ia.md)). Poupa minutos de espera na Genie em perguntas repetidas.

### CST-6 · Streaming da resposta
**P2 · M · Médio**

Entregar a introdução e o texto do especialista à medida que são gerados. Não reduz o custo, mas reduz muito o **tempo até a primeira informação útil**, que é o que o usuário sente. Depende de [CNF-6](05-confiabilidade-e-escala.md).

### CST-7 · Aproveitar *prompt caching* do provedor
**P2 · S · Médio**

Os prompts já começam por blocos longos e estáveis (instruções do planejador, perfil do especialista). Manter o conteúdo variável sempre no fim e **medir a taxa de acerto de cache** com os contadores de [OBS-1](01-observabilidade.md).

---

## Indicadores a acompanhar

| Indicador | Meta inicial sugerida |
|---|---|
| Custo médio por pergunta que delega | definir após 2 semanas de dados |
| Chamadas de modelo por execução (média e p95) | p95 < 15 |
| Tokens por papel | acompanhar tendência |
| Tempo médio gasto esperando a Genie | acompanhar (maior parcela provável da latência) |

Próximo: [Produto e experiência](07-produto-e-experiencia.md).
