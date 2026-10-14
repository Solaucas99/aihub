# Hub de IA — documentação para pessoas

Estes documentos explicam o Hub de IA em linguagem de produto e de arquitetura, sem exigir que você leia o código. Para a especificação exata (reconstruir o projeto), veja `specs/`, `Modelling/` e `Mermaid/`.

**Para quem é:** gestores, lideranças técnicas e engenheiros que precisam entender o que o Hub faz, por que foi desenhado assim e onde estão os limites.

## Leia nesta ordem

| # | Documento | Responde a |
|---|---|---|
| 1 | [Visão geral](01-visao-geral.md) | O que é o Hub, que problema resolve e quais princípios o guiam? |
| 2 | [A jornada de uma pergunta](02-jornada-de-uma-pergunta.md) | O que acontece desde a pergunta até a resposta final? Onde estão as decisões? |
| 3 | [Quem faz o quê](03-quem-faz-o-que.md) | Qual o papel do planejador, dos especialistas, do validador e do comunicador? |
| 4 | [Memória, contexto e artefatos](04-memoria-contexto-e-artefatos.md) | Como o Hub lembra, troca de assunto e reaproveita resultados? |
| 5 | [Dados e infraestrutura](05-dados-e-infraestrutura.md) | Onde ficam os dados, quais serviços externos e quais configurações? |
| 6 | [Extensibilidade](06-extensibilidade.md) | Quão fácil é criar um novo especialista ou conectar uma nova fonte? |
| 7 | [Garantias e limites](07-garantias-e-limites.md) | O que o sistema garante hoje e o que ainda não faz? |
| 8 | [Glossário](08-glossario.md) | Significado dos termos usados. |

Há também uma apresentação: **[Hub-de-IA.pptx](Hub-de-IA.pptx)** (15 slides, com notas do apresentador em cada um).

## Diagramas

Os diagramas ficam em [diagramas/](diagramas/), cada um com a fonte Mermaid (`.mmd`, editável) e a imagem (`.png`):

| Diagrama | Fonte | Imagem |
|---|---|---|
| Jornada de uma pergunta (fluxo de decisões) | [.mmd](diagramas/jornada-da-pergunta.mmd) | [.png](diagramas/jornada-da-pergunta.png) |
| Ciclo de uma tarefa (executar, validar, quatro vereditos) | [.mmd](diagramas/ciclo-de-uma-tarefa.mmd) | [.png](diagramas/ciclo-de-uma-tarefa.png) |
| Quem fala com quem, em ordem | [.mmd](diagramas/sequencia-executiva.mmd) | [.png](diagramas/sequencia-executiva.png) |
| Visão em camadas | [.mmd](diagramas/visao-em-camadas.mmd) | [.png](diagramas/visao-em-camadas.png) |

## Base dos fatos

Os números e comportamentos descritos aqui foram conferidos no código-fonte e nos arquivos de configuração do projeto (por exemplo: 13 coleções no MongoDB, até 3 tentativas por tarefa, 4 tarefas em paralelo, 72 testes automatizados). Quando algo é uma limitação conhecida, ela está dita de forma explícita em [Garantias e limites](07-garantias-e-limites.md).
