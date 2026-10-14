# Frontend do Hub de IA

Interface estatica simples para testar a API do Hub de IA.

## Como usar

1. Suba a API Java em `http://localhost:8080`.
2. Abra `frontend/index.html` no navegador.
3. Ajuste o `User ID` e a URL da API em **Configuracoes** (canto inferior da barra lateral), se precisar.
4. Envie uma mensagem.

## Interface

Layout inspirado no Claude: barra lateral com as conversas recentes, coluna central de leitura, campo de mensagem arredondado e tema claro/escuro (segue o sistema; o botao do topo alterna).

- **Atividade do agente.** Enquanto o Hub trabalha, aparece uma linha com o passo atual; clique para ver os logs da execucao. Ao terminar, ela fica recolhida como "Analise concluida".
- **Artefatos.** O botao "Artefatos" abre um painel lateral com os resultados produzidos pelos agentes (insights, briefings), com versao, status (ativo ou substituido) e a marca "Desatualizado" quando a base mudou. Clique em um artefato para ler o conteudo.
- **Tabelas e graficos.** Tabelas do Genie e blocos `vega-lite` sao renderizados na resposta, acompanhando o tema.
- **Link direto.** A URL guarda a conversa aberta: `index.html?c=<conversation_id>`. Parametros opcionais: `panel=1` abre os artefatos e `theme=light|dark` forca o tema.

## API consumida

- `POST /conversation`
- `POST /conversations/{conversationId}`
- `GET /conversations`
- `GET /conversations/{conversationId}`
- `GET /conversations/{conversationId}/agent-runs`
- `GET /conversations/{conversationId}/agent-runs/{agentRunId}`
- `GET /conversations/{conversationId}/artifacts`

Os POSTs retornam `202 Accepted`, com `conversation_id` e `message_id`. Quando o planner cria um AgentRun, o front o encontra pelo `triggering_message_id` e acompanha o progresso a cada 2,2 segundos. Ao ver o estado final, faz uma ultima leitura das mensagens, pois a resposta final e gravada logo apos a run terminar.
