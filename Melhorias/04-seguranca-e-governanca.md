# 4. Segurança e governança

**Tese:** é o ponto que mais bloqueia um uso além do laboratório. Nenhum item abaixo é difícil, mas **SEG-1 é pré-requisito para mostrar o Hub a qualquer pessoa fora da equipe**.

## Estado atual (verificado no código)

| Tema | Situação |
|---|---|
| Autenticação | `AuthHeaders.requireUserId` só exige que o cabeçalho `Authorization` **não seja vazio** e usa o próprio valor como id do usuário. Quem enviar `Authorization: maria` age como "maria". |
| Isolamento entre usuários | Bom: toda leitura passa por `requireOwned(userId, conversationId)`. Mas ele é tão forte quanto a identidade, que hoje é declarada pelo cliente. |
| CORS | `allowedOriginPatterns("*")`, todos os cabeçalhos. |
| Validação de entrada | `SendConversationMessageRequest(String message)` sem `@NotBlank` nem `@Size`: sem limite de tamanho de mensagem. |
| Credenciais | Padrões de desenvolvimento (`admin/admin123`) estão no `docker-compose.yaml` e como valor padrão de `spring.mongodb.uri`. |
| Retenção de dados | Nenhum índice TTL; `task_executions` guarda mensagens brutas e saídas das ferramentas. |
| Frontend | Já usa **DOMPurify** sobre o markdown renderizado (com *fallback* que escapa HTML). Bom. Depende de bibliotecas carregadas de CDN. |
| Ferramentas | `AgentSkillAccessPolicy` limita as ferramentas de cada especialista (habilidade → ferramenta → provider habilitado). Bom. |

---

### SEG-1 · Autenticação real (OIDC/JWT)
**P0 · M · Crítico**

- **Proposta:** `spring-boot-starter-oauth2-resource-server`; o id do usuário passa a ser o `sub` do token validado; papéis vêm de *claims*. Remover o campo "id do usuário" da tela e usar o fluxo de login do provedor de identidade corporativo.
- **Pronto quando:** uma requisição sem token válido recebe 401 e não existe mais caminho para escolher o próprio `user_id`.

### SEG-2 · Autorização por papel (catálogo e dados)
**P1 · M · Alto**

- **Hoje:** todo usuário enxerga todo contexto de trabalho e todo especialista.
- **Proposta:** associar papéis a contextos/especialistas e **filtrar o `available_work_contexts` entregue ao planejador**; o planejador não pode delegar para o que o usuário não pode usar. Para dados, propagar a identidade (ou um papel) à Genie (*on-behalf-of*, um espaço por papel) para que a segurança por linha/coluna da fonte seja respeitada.

### SEG-3 · CORS, limites de entrada e limitação de taxa
**P0 · S · Alto**

- Lista explícita de origens; `@NotBlank` e `@Size(max=…)` na mensagem; limite de tamanho do corpo; limitação de taxa por usuário (por exemplo 30 mensagens/min) e teto de execuções simultâneas por usuário. Devolver 429 com `Retry-After`.

### SEG-4 · Segredos e credenciais
**P0 · S · Alto**

- Remover credenciais padrão do repositório (`.env.example` sem valores reais); chaves da OpenAI/Genie vindas de um cofre de segredos; MongoDB com usuário de aplicação de privilégio mínimo, TLS e rede fechada; rotação documentada.

### SEG-5 · Defesa contra injeção de prompt
**P1 · M · Alto**

- **Superfície real:** o texto do usuário, a saída da Genie e o conteúdo dos artefatos entram em prompts de outros modelos.
- **Proposta:**
  1. Delimitar claramente dados não confiáveis (`<dados>…</dados>`) e instruir os modelos a **nunca obedecer instruções contidas neles**.
  2. Validar os argumentos de cada chamada de ferramenta contra o `schema()` do `ProviderTool` antes de executar.
  3. Ferramentas somente de leitura por padrão; ações com efeito colateral exigem confirmação humana (reaproveitar o fluxo HITL).
  4. Casos de ataque no conjunto de avaliação ([QLD-1](03-qualidade-e-avaliacao-de-ia.md)): "ignore as instruções anteriores…", dados com instruções embutidas, pedido de revelar o prompt.

### SEG-6 · Dados pessoais, retenção e LGPD
**P1 · M · Alto**

- Inventário do que é guardado (mensagens, saídas de ferramenta, perfis de clientes retornados pela Genie).
- **TTL** configurável para `task_executions` e `messages` (por exemplo 90 dias), arquivamento para o que precisa ser auditado.
- **Mascaramento** de dados pessoais em logs e em métricas.
- Rotina de **exclusão por usuário** (direito ao esquecimento) que cobre conversas, execuções, artefatos, pedidos e sessões de provider.
- Base legal e finalidade documentadas.

### SEG-7 · Auditoria imutável
**P2 · M · Médio**

Registro append-only de "quem perguntou, qual especialista consultou qual fonte, quando" (derivável de `task_executions.tool_calls` + identidade). Exportável para o SIEM.

### SEG-8 · Cotas e orçamento por usuário
**P2 · M · Médio**

Tokens/custo por usuário e por organização por dia/mês, com bloqueio gracioso e aviso (usa [OBS-1](01-observabilidade.md)).

### SEG-9 · Endurecer o frontend
**P1 · S · Médio**

- Fixar versão e **SRI** (hash de integridade) das bibliotecas de CDN ou publicá-las localmente; **CSP** restritiva.
- **Vega-Lite:** a especificação do gráfico vem de um modelo. Restringir o carregador do Vega (sem `data.url` para hosts arbitrários) para que um gráfico induzido por injeção não faça o navegador buscar URLs externas.

---

**Ordem recomendada:** SEG-1 + SEG-3 + SEG-4 (pré-requisito de exposição) → SEG-6 + SEG-2 → SEG-5 + SEG-9 → SEG-7/8.

Próximo: [Confiabilidade e escala](05-confiabilidade-e-escala.md).
