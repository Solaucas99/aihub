# 3. Quem faz o quê

Quatro papéis são feitos por modelos de linguagem; o restante é código determinístico. Essa divisão é intencional: **o que exige julgamento é do modelo; o que exige garantia é do código.**

## Os quatro agentes (modelos de linguagem)

### Planejador
- **Entrada:** a mensagem atual, as mensagens anteriores, o que cada especialista já fez nesta conversa (pedido e resultado), os artefatos, o contexto ativo e pausados, as perguntas pendentes e o catálogo de contextos disponíveis (com seus especialistas e o que cada um produz e consome).
- **Saída (formato rígido):** responder ou não, mensagem ao usuário, ação sobre o contexto (`NONE`, `KEEP`, `ACTIVATE`), pergunta pendente respondida, artefato a reativar e a lista de tarefas (descrição, conteúdo enriquecido, especialista).
- **Não faz:** não acessa dados, não calcula, não gera gráfico ou tabela.
- **Limite de segurança:** a decisão só é gravada depois que o Hub confere que o contexto e os especialistas existem e se relacionam.

### Especialista (subagente)
- Recebe uma tarefa autocontida e usa **somente** as ferramentas que seu perfil permite (cadeia de acesso: especialista → habilidades → ferramentas → provider habilitado).
- Roda um laço "modelo ↔ ferramenta" até ter a resposta; não pode alegar consultas que não fez.
- Gráficos e tabelas chegam da ferramenta em formato neutro e são referenciados na resposta por marcadores; a montagem final acontece em tempo de execução.
- Hoje há dois: **Mensuração** (desempenho de campanhas, métricas, KPIs, conversão; produz o tipo de resultado `INSIGHT`) e **Mapa de Clientes** (perfil, segmentação, renda, histórico, propensão de compra).

### Validador (LLM como juiz)
- Compara a resposta com o objetivo da tarefa, com a entrada recebida e com a memória curta da conversa.
- Devolve **apenas** `CONTINUE`, `RETRY`, `HITL` ou `CANCEL`, com feedback. Ele só julga; as regras de retry, cancelamento e pergunta ao usuário são aplicadas pelo código, não por ele.

### Comunicador
- Escreve uma **introdução curta**, em markdown simples, sem IDs, ferramentas ou detalhes internos, e sem reproduzir a resposta dos especialistas (que vem logo depois).
- Há três variações: execução concluída, parcial (com pendências) e cancelada. Se o modelo falhar, há um texto padrão de segurança.

## O código que garante o funcionamento

| Componente | Responsabilidade |
|---|---|
| **API REST** | Recebe mensagens, devolve o status e as listas. Identifica o usuário pelo cabeçalho `Authorization` (veja os limites em [Garantias e limites](07-garantias-e-limites.md)). |
| **Worker de conversa** | Processa cada mensagem em segundo plano; serializa o planejamento por conversa. |
| **Tratador da decisão** | Valida a decisão do planejador antes de gravar qualquer coisa; cria a execução. |
| **Serviço de execuções** | Mantém o ciclo de vida da execução e das tarefas com atualizações atômicas no banco. |
| **Orquestrador** | Roda as tarefas em paralelo, respeitando dependências; aplica o veredito do validador; fecha a execução. |
| **Resolvedor de dependências** | Ordena tarefas pelo catálogo (produz/consome); se houver ciclo, roda em paralelo em vez de travar. |
| **Política de acesso** | Decide quais habilidades e ferramentas cada especialista pode usar. |
| **Serviço de artefatos** | Versiona e marca resultados (ativo, substituído, desatualizado). |
| **Serviço de pedidos ao usuário** | Guarda e fecha as perguntas pendentes (HITL). |
| **Providers** | Conectores de fonte de dados (hoje, Genie): consulta, leitura da resposta, tabelas e gráficos. |

## Por que dois níveis de decisão (planejador e validador)?

O planejador decide **o que fazer antes** de gastar recursos; o validador decide **se o que foi feito presta** antes de mostrar ao usuário. Isso separa a pergunta "devo delegar?" da pergunta "este resultado é confiável?", e evita que o mesmo modelo corrija a própria resposta.

Próximo: [Memória, contexto e artefatos](04-memoria-contexto-e-artefatos.md).
