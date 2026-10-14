# Melhorias para o Hub de IA virar um case de sucesso

Anotações de evolução, baseadas no **estado real do código** (cada documento começa com um quadro "Estado atual" verificado). Cobrem observabilidade, extensibilidade, qualidade de IA, segurança, confiabilidade, custo, produto e engenharia, e fecham com um plano para **provar** o resultado.

**Escrito para:** a equipe que vai evoluir o Hub e quem decide as prioridades (liderança técnica e de produto).

## Como ler

| # | Documento | Pergunta que responde |
|---|---|---|
| 1 | [Observabilidade](01-observabilidade.md) | Quanto custou, quanto demorou e por que decidiu assim? |
| 2 | [Extensibilidade](02-extensibilidade.md) | Posso criar um agente novo sem código? E uma fonte nova? |
| 3 | [Qualidade e avaliação de IA](03-qualidade-e-avaliacao-de-ia.md) | Como sei que está acertando e que não regrediu? |
| 4 | [Segurança e governança](04-seguranca-e-governanca.md) | Posso expor isso a outras pessoas? |
| 5 | [Confiabilidade e escala](05-confiabilidade-e-escala.md) | O que acontece quando algo falha ou há carga? |
| 6 | [Custo e desempenho](06-custo-e-desempenho.md) | Quanto custa e como mantenho previsível? |
| 7 | [Produto e experiência](07-produto-e-experiencia.md) | O que o usuário vê e sente? |
| 8 | [Engenharia e entrega](08-engenharia-e-entrega.md) | Qualquer pessoa sobe e mantém isso sozinha? |
| 9 | [Case de sucesso](09-case-de-sucesso.md) | O que provar, como medir e como contar? |

Cada melhoria tem um id (por exemplo `OBS-1`), **prioridade** (P0 fundação, P1 piloto, P2 diferencial), **esforço** (S até ~3 dias, M até ~2 semanas, L mais que isso) e **impacto**. Os esforços são estimativas de ordem de grandeza para uma pessoa familiarizada com o código.

## Os achados mais importantes (leia isto primeiro)

1. **O custo por pergunta é invisível.** O cliente da OpenAI descarta o campo `usage` e o pior caso teórico passa de 50 chamadas por pedido ([OBS-1](01-observabilidade.md), [CST-1](06-custo-e-desempenho.md)).
2. **A identidade é declarada pelo cliente.** O valor do cabeçalho `Authorization` *é* o id do usuário, e o CORS aceita qualquer origem. Nada disso deve ser exposto fora da rede da equipe ([SEG-1/3](04-seguranca-e-governanca.md)).
3. **Falhas externas não são tratadas.** Sem retry/backoff para OpenAI e Genie; execuções interrompidas ficam `RUNNING` para sempre; a fila de mensagens é em memória ([CNF-1/2/4](05-confiabilidade-e-escala.md)).
4. **O Hub é de instância única hoje.** As travas por conversa são locais; escalar horizontalmente exige [CNF-4](05-confiabilidade-e-escala.md).
5. **A promessa de extensibilidade ainda não foi provada.** O catálogo é dado, mas não há validação, tela nem teste que demonstre "agente novo sem código" ([EXT-8/1](02-extensibilidade.md)).
6. **A qualidade não é medida de forma contínua.** Há um cenário vivo do planejador; o validador não tem avaliação ([QLD-1/2](03-qualidade-e-avaliacao-de-ia.md)).
7. **A automação que mantém as specs corretas está fora do repositório.** Os geradores vivem em uma pasta temporária ([ENG-3](08-engenharia-e-entrega.md)).

## Se só der para fazer dez coisas

`OBS-2` logs com correlação → `OBS-1` tokens e custo → `OBS-3` métricas → `CNF-2` retry e disjuntor → `CNF-1` execuções órfãs → `SEG-1` autenticação real → `QLD-1` conjunto de avaliação → `EXT-8` prova do agente Briefing → `ENG-3` geradores no repositório → `ENG-2` CI com Testcontainers.

## Roadmap em ondas


### Onda 0 · Fundação de confiança (14 itens)

| Id | Melhoria | Área | Esforço | Impacto |
|---|---|---|---|---|
| [OBS-1](01-observabilidade.md) | Medir tokens, custo e latência de cada chamada de modelo | Observabilidade | S–M | Alto |
| [CNF-1](05-confiabilidade-e-escala.md) | Recuperar execuções órfãs | Confiabilidade | S–M | Alto |
| [CNF-2](05-confiabilidade-e-escala.md) | Retry, backoff e disjuntor para OpenAI e Genie | Confiabilidade | S–M | Alto |
| [ENG-1](08-engenharia-e-entrega.md) | Dockerfile e *compose* completo | Engenharia | S | Alto |
| [ENG-2](08-engenharia-e-entrega.md) | CI com Testcontainers | Engenharia | M | Alto |
| [ENG-3](08-engenharia-e-entrega.md) | Trazer os geradores e verificadores para o repositório | Engenharia | S | Alto |
| [EXT-8](02-extensibilidade.md) | Prova de extensibilidade: o agente "Briefing" e um teste de aceitação | Extensibilidade | S | Alto |
| [OBS-2](01-observabilidade.md) | Logs estruturados com correlação | Observabilidade | S | Alto |
| [OBS-3](01-observabilidade.md) | Actuator e métricas (Micrometer) | Observabilidade | S | Alto |
| [OBS-9](01-observabilidade.md) | Health e readiness de verdade | Observabilidade | S | Médio |
| [QLD-1](03-qualidade-e-avaliacao-de-ia.md) | Conjunto de avaliação (evals) do planejador | Qualidade de IA | M | Alto |
| [SEG-1](04-seguranca-e-governanca.md) | Autenticação real (OIDC/JWT) | Segurança | M | Crítico |
| [SEG-3](04-seguranca-e-governanca.md) | CORS, limites de entrada e limitação de taxa | Segurança | S | Alto |
| [SEG-4](04-seguranca-e-governanca.md) | Segredos e credenciais | Segurança | S | Alto |

### Onda 1 · Piloto sólido (34 itens)

| Id | Melhoria | Área | Esforço | Impacto |
|---|---|---|---|---|
| [CNF-3](05-confiabilidade-e-escala.md) | Contrapressão e limites globais de concorrência | Confiabilidade | M | Alto |
| [CNF-5](05-confiabilidade-e-escala.md) | Idempotência no envio | Confiabilidade | S | Médio |
| [CNF-7](05-confiabilidade-e-escala.md) | Cancelamento pelo usuário e prazo máximo por execução | Confiabilidade | M | Alto |
| [CNF-8](05-confiabilidade-e-escala.md) | Retenção, arquivamento e crescimento do banco | Confiabilidade | S | Médio |
| [CNF-9](05-confiabilidade-e-escala.md) | Backup, réplica e recuperação | Confiabilidade | S | Alto |
| [CST-1](06-custo-e-desempenho.md) | Orçamento de chamadas por execução | Custo e desempenho | S | Alto |
| [CST-2](06-custo-e-desempenho.md) | Modelo por papel | Custo e desempenho | S | Alto |
| [CST-3](06-custo-e-desempenho.md) | Validador com pré-checagens determinísticas | Custo e desempenho | S | Médio |
| [ENG-4](08-engenharia-e-entrega.md) | OpenAPI e versionamento da API | Engenharia | S | Médio |
| [ENG-5](08-engenharia-e-entrega.md) | Regras arquiteturais e cobertura | Engenharia | S | Médio |
| [ENG-6](08-engenharia-e-entrega.md) | Controle de versão do esquema do MongoDB | Engenharia | M | Médio |
| [ENG-7](08-engenharia-e-entrega.md) | Limpeza do repositório | Engenharia | S | Baixo |
| [ENG-8](08-engenharia-e-entrega.md) | Perfis e configuração por ambiente | Engenharia | S | Médio |
| [EXT-1](02-extensibilidade.md) | API e administração do catálogo, com validação | Extensibilidade | M | Alto |
| [EXT-3](02-extensibilidade.md) | Provider MCP (Model Context Protocol) | Extensibilidade | M | Alto |
| [EXT-5](02-extensibilidade.md) | Abstração de modelo de linguagem | Extensibilidade | M | Alto |
| [EXT-11](02-extensibilidade.md) | Planejador ciente do contexto de cada execução | Extensibilidade | S | Médio |
| [OBS-4](01-observabilidade.md) | Rastreamento distribuído (OpenTelemetry) | Observabilidade | M | Alto |
| [OBS-5](01-observabilidade.md) | Linha do tempo da execução (API e tela) | Observabilidade | M | Alto |
| [OBS-6](01-observabilidade.md) | Painéis, SLOs e alertas | Observabilidade | M | Alto |
| [OBS-7](01-observabilidade.md) | Registrar a decisão do planejador e o motivo | Observabilidade | S | Médio |
| [OBS-8](01-observabilidade.md) | Versionar os prompts | Observabilidade | S | Médio |
| [PRD-1](07-produto-e-experiencia.md) | "Como cheguei nisso" | Produto | M | Alto |
| [PRD-2](07-produto-e-experiencia.md) | Feedback 👍/👎 com motivo | Produto | S | Alto |
| [PRD-3](07-produto-e-experiencia.md) | Cancelar, editar e retomar | Produto | M | Médio |
| [QLD-2](03-qualidade-e-avaliacao-de-ia.md) | Calibrar o validador (juiz) | Qualidade de IA | M | Alto |
| [QLD-3](03-qualidade-e-avaliacao-de-ia.md) | Gate de regressão de prompts no CI | Qualidade de IA | S | Alto |
| [QLD-4](03-qualidade-e-avaliacao-de-ia.md) | Provedores falsos e testes ponta a ponta determinísticos | Qualidade de IA | M | Médio |
| [QLD-5](03-qualidade-e-avaliacao-de-ia.md) | Testes de falha e caos | Qualidade de IA | M | Médio |
| [QLD-7](03-qualidade-e-avaliacao-de-ia.md) | Evidência e rastreabilidade nas respostas | Qualidade de IA | M | Alto |
| [SEG-2](04-seguranca-e-governanca.md) | Autorização por papel (catálogo e dados) | Segurança | M | Alto |
| [SEG-5](04-seguranca-e-governanca.md) | Defesa contra injeção de prompt | Segurança | M | Alto |
| [SEG-6](04-seguranca-e-governanca.md) | Dados pessoais, retenção e LGPD | Segurança | M | Alto |
| [SEG-9](04-seguranca-e-governanca.md) | Endurecer o frontend | Segurança | S | Médio |

### Onda 2 · Diferenciais (20 itens)

| Id | Melhoria | Área | Esforço | Impacto |
|---|---|---|---|---|
| [CNF-4](05-confiabilidade-e-escala.md) | Fila durável e escala horizontal | Confiabilidade | L | Alto |
| [CNF-6](05-confiabilidade-e-escala.md) | Tempo real (SSE) | Confiabilidade | M | Médio |
| [CST-4](06-custo-e-desempenho.md) | Memória longa com resumo rolante | Custo e desempenho | M | Médio |
| [CST-5](06-custo-e-desempenho.md) | Cache de consultas às fontes | Custo e desempenho | M | Médio |
| [CST-6](06-custo-e-desempenho.md) | Streaming da resposta | Custo e desempenho | M | Médio |
| [CST-7](06-custo-e-desempenho.md) | Aproveitar *prompt caching* do provedor | Custo e desempenho | S | Médio |
| [EXT-2](02-extensibilidade.md) | Versão do catálogo por execução | Extensibilidade | S | Médio |
| [EXT-4](02-extensibilidade.md) | Ferramentas HTTP declarativas | Extensibilidade | M | Alto |
| [EXT-6](02-extensibilidade.md) | Planejamento em mais de uma passada | Extensibilidade | L | Médio |
| [EXT-7](02-extensibilidade.md) | Renderizadores e exportadores plugáveis | Extensibilidade | M | Médio |
| [EXT-9](02-extensibilidade.md) | Eventos de saída e novos canais | Extensibilidade | M | Médio |
| [EXT-10](02-extensibilidade.md) | Multi-tenant | Extensibilidade | L | Alto |
| [EXT-12](02-extensibilidade.md) | Base de conhecimento (RAG) como provider | Extensibilidade | L | Alto |
| [PRD-4](07-produto-e-experiencia.md) | HITL com respostas rápidas | Produto | S | Médio |
| [PRD-5](07-produto-e-experiencia.md) | Exportar e compartilhar | Produto | M | Médio |
| [PRD-6](07-produto-e-experiencia.md) | Descoberta: "o que posso perguntar?" | Produto | S | Médio |
| [PRD-7](07-produto-e-experiencia.md) | Acessibilidade e internacionalização | Produto | M | Médio |
| [QLD-6](03-qualidade-e-avaliacao-de-ia.md) | Feedback humano alimentando o conjunto dourado | Qualidade de IA | M | Alto |
| [SEG-7](04-seguranca-e-governanca.md) | Auditoria imutável | Segurança | M | Médio |
| [SEG-8](04-seguranca-e-governanca.md) | Cotas e orçamento por usuário | Segurança | M | Médio |

**Onda 0** deixa o sistema *observável, seguro o bastante para um grupo fechado e reproduzível*; **Onda 1** o torna *confiável para um piloto real*; **Onda 2** constrói os *diferenciais* (tempo real, multi-tenant, RAG, canais).

## Dependências entre melhorias

- `OBS-1` → `CST-1`, `CST-2`, `SEG-8`, KPIs do case.
- `EXT-5` (abstração de modelo) → `OBS-1`, `CST-2`, fallback de `CNF-2`.
- `OBS-5` + `QLD-7` → `PRD-1` ("Como cheguei nisso").
- `CNF-6` (SSE) → `CST-6` (streaming) e etapas em tempo real.
- `SEG-1` → `SEG-2`, `EXT-10` (multi-tenant).
- `OBS-8` (versão de prompt) + `QLD-1/2` → `QLD-3` (gate de regressão).

## O que deliberadamente não está aqui

Reescrever a arquitetura. O desenho atual (planejador → tarefas em paralelo → validador → comunicador, com o banco como fonte da verdade) é o ponto forte do projeto; as melhorias o tornam **mensurável, seguro e demonstrável**, não diferente.
