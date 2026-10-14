# 4. Memória, contexto e artefatos

O Hub lembra de três formas diferentes, cada uma para um propósito.

## 1. Memória de conversa (o histórico)

As mensagens da conversa são entregues ao planejador a cada nova pergunta. Antes disso passam por um **sanitizador** que remove marcas internas de conteúdo visual, para que o modelo leia texto limpo. Por isso referências como "isso", "esses dados" e "continue" podem ser resolvidas.

## 2. Memória curta dos especialistas

Cada especialista recebe a lembrança das **suas últimas 5 tarefas** na conversa (pedido e resultado, truncados), considerando uma janela das 40 execuções mais recentes. O validador usa a memória curta para julgar se a resposta é coerente com o que já foi dito.

Detalhes que protegem a qualidade da memória:
- os horários são **monotônicos** (nunca dois registros no mesmo milissegundo), para que a ordem seja sempre a real;
- tentativas repetidas de uma mesma tarefa não "empurram" tarefas anteriores para fora da janela.

## 3. Contextos de trabalho (WorkContext)

Um **contexto de trabalho** é um "modo" do catálogo: um conjunto de especialistas e instruções. Hoje existe um: **Análise de Dados** (Mensuração e Mapa de Clientes).

- O catálogo é dado no MongoDB; na conversa, cada contexto vira uma **instância**, que fica `ACTIVE` ou `PAUSED`.
- **No máximo um contexto fica ativo por conversa.** Ativar outro pausa o anterior; voltar a um contexto antigo **reaproveita a mesma instância** (com o último resultado registrado).
- O contexto **organiza** os especialistas, mas **nunca limita a conversa**: o usuário pode fazer qualquer pergunta a qualquer momento.

### Como o planejador troca de contexto e "leva os dados"

Os dados não são copiados de um contexto para outro. O que acontece é:

1. O planejador lê `active_context` e `paused_contexts` (com a última execução de cada um) e os **resultados recentes e artefatos da conversa inteira**.
2. Para continuar no contexto atual ele usa `KEEP`; para trocar ou voltar a outro, `ACTIVATE` com o id do catálogo.
3. Ao escrever a tarefa, ele **embute no conteúdo enriquecido** o que o novo especialista precisa saber (tema, métrica, período, filtros, e o resultado a reaproveitar).
4. Se o novo especialista *consome* um tipo de resultado, o Hub injeta no prompt dele os artefatos desse tipo.

Ou seja: o que atravessa contextos são **a conversa, os artefatos e o texto da tarefa**, não uma memória própria de cada contexto.

## 4. Artefatos (resultados versionados)

Quando uma tarefa é aprovada e o especialista **produz** um tipo de resultado (ex.: `INSIGHT`), o Hub publica um **artefato**:

| Atributo | Significado |
|---|---|
| **tipo** e **versão** | Cada nova publicação do mesmo tipo na conversa cria a versão seguinte (a publicação concorrente é protegida por índice único e novas tentativas) |
| **status** | `ACTIVE` (vigente) ou `SUPERSEDED` (substituído por versão mais nova) |
| **stale** | Calculado: indica que o artefato pode estar desatualizado |
| **conteúdo e visuais** | O texto e os gráficos/tabelas em formato neutro |

O planejador enxerga a lista de artefatos (tipo, versão, status, título e resumo). Se um artefato vigente já responde ao pedido, ele pode **responder direto** ou criar tarefas só para o que falta; se o usuário muda filtros, período ou foco, ou pede dados mais novos, ele delega de novo. Pedidos como "volta pro anterior" **reativam uma versão antiga** (`reactivate_artifact_id`).

## 5. Perguntas pendentes ao usuário (HITL)

Quando o validador devolve `HITL`, a pergunta vira um **pedido de informação** `PENDING`. Ele **não bloqueia** a conversa: o usuário pode responder quando quiser; a resposta chega como mensagem normal, o planejador a associa ao pedido (`related_human_input_request_id`) e o pedido passa a `ANSWERED`.

## Mapa rápido

| Preciso de… | Onde está |
|---|---|
| O que foi dito | Mensagens da conversa |
| O que cada especialista fez e devolveu | Execuções e tarefas (e a memória curta do especialista) |
| Resultado reaproveitável e versionado | Artefatos |
| Em que "modo" estamos | Instância de contexto ativa (e as pausadas) |
| O que ainda aguarda o usuário | Pedidos de informação pendentes |

Próximo: [Dados e infraestrutura](05-dados-e-infraestrutura.md).
