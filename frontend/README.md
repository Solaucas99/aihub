# Frontend do Hub de IA

Interface estatica simples para testar a API do Hub de IA.

## Como usar

1. Suba a API Java em `http://localhost:8080`.
2. Abra `frontend/index.html` no navegador.
3. Informe um `User ID` de teste e a base da API.
4. Envie uma mensagem.

O front consome:

- `POST /conversation`
- `POST /conversations/{conversationId}`
- `GET /conversations`
- `GET /conversations/{conversationId}`
- `GET /conversation/{conversationId}/messages`
- `GET /conversations/{conversationId}/agent-runs`
- `GET /conversations/{conversationId}/agent-runs/{agentRunId}`

Os POSTs retornam `202 Accepted`, com `conversation_id` e `message_id`. Quando o planner cria um AgentRun, o front o encontra pelo `triggering_message_id` e exibe o polling de logs a cada 2.2 segundos.
