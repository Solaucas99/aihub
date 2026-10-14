# 1. Visão geral

## Em uma frase

O Hub de IA é um assistente conversacional que **decide sozinho se responde direto ou aciona especialistas que consultam dados reais**, confere a qualidade do que eles devolvem e entrega uma resposta única, com texto, tabelas e gráficos.

## O problema

Um chatbot comum responde de memória e pode inventar números. Em análise de campanhas e clientes isso é inaceitável: a resposta precisa vir **da fonte de dados**, ser **conferida** e ser **reaproveitável** na conversa seguinte ("agora mostre isso em gráfico").

## A solução em quatro papéis

| Papel | O que faz | Quem é |
|---|---|---|
| **Planejador** | Lê a conversa e decide: responder, perguntar ou delegar | Um modelo de linguagem (OpenAI) com instruções estritas |
| **Especialistas** | Consultam a fonte de dados e produzem a análise | Modelos de linguagem com acesso controlado a ferramentas (hoje: Genie) |
| **Validador** | Julga cada resposta antes de ela chegar ao usuário | Um modelo de linguagem "juiz" |
| **Comunicador** | Escreve a introdução da resposta final | Um modelo de linguagem |

O planejador **nunca calcula nem inventa números**: o que exige dados é sempre delegado a um especialista.

## Princípios de design

1. **A conversa nunca trava.** O usuário recebe confirmação imediata; o trabalho pesado roda em segundo plano e o andamento é consultado por polling. Perguntas pendentes ao usuário não bloqueiam novas mensagens.
2. **Dados antes de opinião.** Números, tabelas e gráficos só saem de especialistas que consultaram a fonte.
3. **Todo resultado é conferido.** Nada chega ao usuário sem passar pelo validador, com até 3 tentativas por tarefa.
4. **O banco é a fonte da verdade.** Todo o estado (conversas, execuções, tarefas, artefatos, catálogo) fica no MongoDB. Não há outro armazenamento (o Redis foi removido).
5. **Conteúdo neutro de fornecedor.** Gráficos e tabelas são montados em tempo de execução a partir de um formato neutro (HTML ou Vega-Lite), sem acoplar a tela ao Genie.
6. **O catálogo é dado, não código.** Especialistas, habilidades e contextos de trabalho vivem no banco; a configuração inicial só é gravada se as coleções estiverem vazias.

## O que existe hoje

| Item | Situação |
|---|---|
| Linguagem e framework | Java 21, Spring Boot 4.1.1 |
| Armazenamento | MongoDB (13 coleções) |
| Modelos de linguagem | OpenAI (modelo configurável; padrão `gpt-5`) |
| Fonte de dados | Genie (Databricks) — único provider hoje |
| Catálogo inicial | 1 contexto de trabalho ("Análise de Dados") com 2 especialistas: **Mensuração** (desempenho de campanhas, KPIs) e **Mapa de Clientes** (perfil, segmentação, propensão) |
| Interface | Chat web estático (HTML/CSS/JS), estilo "Claude UI" |
| Qualidade | 72 testes automatizados (5 são opt-in e só rodam com a chave do modelo real) |

## Como a conversa é organizada

```
Conversa
 ├─ Mensagens (usuário e assistente)
 ├─ Execuções (cada pergunta que exigiu trabalho vira uma "execução")
 │    └─ Tarefas (uma por especialista envolvido)
 ├─ Artefatos (resultados versionados, reaproveitáveis)
 ├─ Contextos de trabalho (em que "modo" a conversa está)
 └─ Pedidos de informação ao usuário (perguntas pendentes)
```

Próximo passo de leitura: [A jornada de uma pergunta](02-jornada-de-uma-pergunta.md).
