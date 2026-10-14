# 10 — Frontend estático (estilo Claude): HTML, CSS, JS e comportamento

> Spec do projeto **Hub de IA**. Leia antes `00-LEIA-ME.md` e `05-api-rest.md` (contrato da API consumida).

## Objetivo

Reproduzir **exatamente** a interface web do Hub: uma página estática sem *build* (HTML + CSS + JS puro) com visual inspirado no Claude (barra lateral de conversas, coluna central de leitura, campo de mensagem arredondado, painel de artefatos, tema claro/escuro), que conversa com a API e mostra o progresso do agente. Como o frontend é pequeno e declarativo, os **quatro arquivos aparecem inteiros e literais** na seção 7: copie-os byte a byte; as seções 1–6 explicam o que fazem e por quê.

## Arquivos cobertos

`frontend/index.html`, `frontend/styles.css`, `frontend/app.js`, `frontend/README.md`.

## Depende de

`05-api-rest.md` (endpoints e JSON). Não depende de nenhuma classe Java.

## 1. Como executar

Abrir `frontend/index.html` direto no navegador (`file://`) **ou** servir a pasta estaticamente. A API deve estar em `http://localhost:8080` (configurável na tela). O CORS do backend permite qualquer origem (ver 02). Bibliotecas externas vêm de CDN (precisa de internet na primeira carga): `vega@5`, `vega-lite@5`, `vega-tooltip@0.35.2` (`build/vega-tooltip.min.js`), `vega-embed@6`, `marked` (`marked.min.js`), `dompurify@3` (`dist/purify.min.js`), todas de `https://cdn.jsdelivr.net/npm/...` exatamente como no `index.html`.

## 2. Layout e responsividade

- Grid de **3 colunas** em `.app` (`--sidebar-w: 288px`, coluna central flexível, painel de artefatos `--panel-w: 460px`): barra lateral (conversas), `main` (topbar + rolagem + compositor) e painel de artefatos. `data-sidebar="open|closed"` e `data-panel="open|closed"` no elemento `#app` controlam as colunas (transição de 0,22 s).
- Coluna de leitura: `--col: 760px` (mensagens e compositor centralizados).
- **Telas ≤ 960px** (`COMPACT_QUERY = "(max-width: 960px)"`): a barra lateral e o painel viram *gavetas* fixas deslizantes, com `#scrim` escurecendo o fundo; começa com a barra lateral **fechada**; abrir um painel fecha o outro.
- Tema: `:root` define o claro; o escuro vem de `prefers-color-scheme: dark` (quando não há `data-theme="light"`) **e** de `:root[data-theme="dark"]`. Cores (claro): fundo `#faf9f5`, barra `#f5f4ed`, destaque `#c96442`; (escuro): fundo `#262624`, barra `#1f1e1d`, destaque `#d97757`. Fontes: sans de sistema para a interface, serifada (`ui-serif, "Iowan Old Style", …, Georgia`) para o texto do assistente e títulos.

## 3. Comportamento (resumo por função)

Estado em `state` (`apiBase`, `userId`, `activeConversationId`, `activeAgentRunId`, `pendingMessageId`, `pollTimer`, `sending`, `renderedIds`, `lastMessages`, `activity`, `activityKey`, `activityOpen`, `pinned`, `artifacts`, `selectedArtifactId`). `apiBase` e `userId` vêm de `localStorage` (`hub_api_base` default `http://localhost:8080`; `hub_user_id` default `user-test-1`); o tema escolhido fica em `hub_theme`.

- **`init`**: aplica ícones, tema, abre/fecha a barra conforme a largura, mostra usuário, liga eventos, renderiza o estado vazio, carrega conversas e, se a URL tem `?c=<id>`, abre essa conversa (e abre o painel de artefatos se tem `panel=...`).
- **Enviar** (`sendMessage`): ignora texto vazio ou envio em andamento; mostra a mensagem do usuário **otimista** (nó com `data-local`); `POST /conversations/{id}` (conversa existente) ou `POST /conversation` (nova) com `{message}` e cabeçalho `Authorization: <userId>`; guarda `conversation_id`/`message_id`; mostra o indicador "pensando"; recarrega a lista; inicia o *polling*.
- **Polling** (`startPolling`): a cada **2200 ms** chama `pollConversation`, que relê `GET /conversations/{id}`, renderiza mensagens **de forma incremental** (só anexa as novas; se a lista de ids muda de forma incompatível refaz tudo), procura a run da mensagem pendente (`GET .../agent-runs?triggering_message_id=`) e, se existe, consulta `GET .../agent-runs/{runId}` (`pollAgentRun`). Se **não há run** e já existe resposta do assistente depois da mensagem, o polling para (resposta direta). Quando a run chega a `FINISHED`/`FAILED`/`CANCELLED`, o polling para e, **700 ms depois**, o front relê mensagens, artefatos e a lista de conversas (`finishRun`), porque o texto final é gravado logo após a run virar terminal.
- **Atividade do agente** (`renderActivity`): enquanto trabalha mostra a linha "Pensando…" ou a mensagem atual da run (efeito *shimmer*); um `<details>` recolhido mostra os últimos 80 logs; ao terminar fica recolhido como "Analise concluida"/"A execucao falhou"/"Execucao cancelada". Reconstrói só se algo mudou (chave `activityKey`) e preserva o estado aberto/fechado do `<details>`.
- **Painel de artefatos**: `GET /conversations/{id}/artifacts`; cartões com tipo, versão, "Ativo/Substituido" e "Desatualizado" (quando `stale`); clicar abre o conteúdo (`rendered_content`) com markdown+gráficos; botão voltar/fechar; `Esc` fecha.
- **Markdown e gráficos** (`renderMarkdownContent`): separa blocos ```` ```vega-lite ```` e renderiza o resto com `marked` + `DOMPurify` (sanitização); os gráficos são embutidos com `vegaEmbed` **adiado com `requestAnimationFrame`** (a largura `container` só é medida depois que o nó está na página), com `padding: 16` na própria spec (o padding do contêiner distorceria a medida), `background: transparent`, tema escuro quando ativo, altura máx. 460 (default 380) e tooltip. Classes antigas `genie-table` são trocadas por `data-table` ao renderizar (compatibilidade).
- **Rolagem fixada ao fim** (`pinned`): segue o conteúdo enquanto o leitor está no final (<120 px) e para se ele sobe; um `ResizeObserver` no `#thread` mantém o fim mesmo quando gráficos carregam depois.
- **Tema**: `?theme=light|dark` na URL força o tema; o botão alterna e grava `hub_theme`; ao alternar, as mensagens são re-renderizadas (os gráficos dependem do tema).
- **Link direto** (`syncUrl`): mantém `?c=<conversation_id>` na barra de endereço (`history.replaceState`, com `try/catch` porque `file://` pode proibir).
- **Configurações**: `<dialog>` com *User ID* e *URL da API*; salvar grava no `localStorage`, limpa a conversa atual e recarrega a lista.
- **Estado vazio**: saudação "Ola! Como posso ajudar hoje?" e 3 sugestões (`SUGGESTIONS`) que preenchem e enviam.
- **Erros**: `request` lança `Error` com `body.message`, `body.error` ou `Erro HTTP <status>`; exibidos como *toast* por 4200 ms.

## 4. API consumida (contrato com a spec 05)

`POST /conversation`, `POST /conversations/{id}`, `GET /conversations`, `GET /conversations/{id}`, `GET /conversations/{id}/agent-runs[?triggering_message_id=]`, `GET /conversations/{id}/agent-runs/{runId}`, `GET /conversations/{id}/artifacts`. Todos com `Authorization: <userId>` e `Content-Type: application/json`. Campos usados: `conversation_id`, `message_id`, `messages[]` (`id`, `role`, `content`), `chat_last_message`, runs (`id`, `status`), `status`/`execution_status`/`current_message`/`logs[]` (`at`, `level`, `message`), artefatos (`id`, `type`, `version`, `status`, `stale`, `title`, `content`, `rendered_content`, `created_at`).

## 5. Armadilhas e decisões não óbvias

1. Renderização **incremental** de mensagens: reembutir gráficos a cada 2,2 s causava piscadas e custo; por isso só se anexa o que é novo.
2. O re-fetch 700 ms após o fim da run compensa a pequena janela em que a run já está terminal mas as mensagens finais ainda não foram gravadas.
3. `vegaEmbed` precisa rodar depois de o nó estar no DOM e com o *padding* dentro da spec; senão o gráfico sai estreito ou estoura a borda.
4. Todo HTML do assistente passa por `DOMPurify`; o conteúdo do usuário é inserido como texto (`textContent`).
5. O front **não conhece** nenhum provider: só renderiza markdown, tabelas HTML (classe `data-table`) e gráficos Vega-Lite que o backend já entrega montados.

## 6. Critérios de aceite

- Abrir `index.html` mostra o estado vazio, a lista de conversas do usuário e o botão "Artefatos"; trocar `?theme=dark|light` troca o visual.
- Enviar uma mensagem exibe a mensagem do usuário imediatamente, a atividade do agente e, ao fim, a resposta (com tabelas e gráficos quando houver).
- `?c=<id>` reabre a conversa; o painel lista os artefatos com os selos corretos; ≤ 960 px a barra e o painel são gavetas.
- Nenhum gráfico é reembutido a cada ciclo de polling.

## 7. Arquivos literais

**Arquivo `frontend/index.html`** (literal):

```html
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="color-scheme" content="light dark">
  <title>Hub de IA</title>
  <link rel="stylesheet" href="./styles.css">
</head>
<body>
  <div id="app" class="app" data-sidebar="open" data-panel="closed">
    <div id="scrim" class="scrim" hidden></div>

    <aside class="sidebar" aria-label="Navegacao">
      <div class="sidebar-head">
        <a class="brand" href="./" aria-label="Hub de IA">
          <span class="brand-mark" data-icon="sparkle"></span>
          <span class="brand-name">Hub de IA</span>
        </a>
        <button id="sidebarClose" class="icon-btn" type="button" aria-label="Recolher barra lateral" data-icon="sidebar"></button>
      </div>

      <button id="newChatButton" class="new-chat" type="button">
        <span data-icon="plus"></span>
        <span>Nova conversa</span>
      </button>

      <nav class="sidebar-body" aria-label="Conversas">
        <div class="sidebar-label">Recentes</div>
        <div id="conversationList" class="conversation-list"></div>
      </nav>

      <div class="sidebar-foot">
        <button id="settingsButton" class="user-chip" type="button" aria-label="Configuracoes">
          <span id="userAvatar" class="avatar-sm">U</span>
          <span class="user-chip-text">
            <strong id="userLabel">user-test-1</strong>
            <small>Configuracoes</small>
          </span>
          <span data-icon="gear"></span>
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <button id="sidebarOpen" class="icon-btn only-collapsed" type="button" aria-label="Abrir barra lateral" data-icon="sidebar"></button>
        <h1 id="conversationTitle" class="topbar-title">Nova conversa</h1>
        <span id="statusPill" class="status-pill" hidden></span>
        <div class="topbar-spacer"></div>
        <button id="themeButton" class="icon-btn" type="button" aria-label="Alternar tema" data-icon="moon"></button>
        <button id="panelButton" class="panel-btn" type="button" aria-label="Artefatos" aria-expanded="false">
          <span data-icon="layers"></span>
          <span>Artefatos</span>
          <span id="artifactCount" class="count" hidden>0</span>
        </button>
      </header>

      <div id="scroller" class="scroller">
        <div id="thread" class="thread">
          <div id="messages" class="messages"></div>
          <div id="activity" class="activity-slot"></div>
        </div>
      </div>

      <div class="composer-wrap">
        <form id="composer" class="composer">
          <textarea id="messageInput" rows="1" placeholder="Como posso ajudar voce hoje?" aria-label="Mensagem"></textarea>
          <div class="composer-row">
            <span class="composer-hint">Enter envia · Shift+Enter quebra a linha</span>
            <button id="sendButton" class="send-btn" type="submit" aria-label="Enviar" data-icon="arrow-up" disabled></button>
          </div>
        </form>
        <p class="footnote">O Hub de IA pode cometer erros. Confira os dados importantes.</p>
      </div>
    </main>

    <aside id="artifactPanel" class="panel" aria-label="Artefatos">
      <header class="panel-head">
        <button id="panelBack" class="icon-btn" type="button" aria-label="Voltar" data-icon="arrow-left" hidden></button>
        <strong id="panelTitle">Artefatos</strong>
        <div class="topbar-spacer"></div>
        <button id="panelClose" class="icon-btn" type="button" aria-label="Fechar" data-icon="close"></button>
      </header>
      <div id="panelBody" class="panel-body"></div>
    </aside>
  </div>

  <dialog id="settingsDialog" class="dialog">
    <form id="settingsForm" method="dialog">
      <h2>Configuracoes</h2>
      <label>
        <span>User ID</span>
        <input id="userIdInput" autocomplete="off" spellcheck="false">
      </label>
      <label>
        <span>URL da API</span>
        <input id="apiBaseInput" autocomplete="off" spellcheck="false">
      </label>
      <div class="dialog-actions">
        <button id="settingsCancel" class="btn" type="button">Cancelar</button>
        <button class="btn primary" type="submit" value="save">Salvar</button>
      </div>
    </form>
  </dialog>

  <script src="https://cdn.jsdelivr.net/npm/vega@5"></script>
  <script src="https://cdn.jsdelivr.net/npm/vega-lite@5"></script>
  <script src="https://cdn.jsdelivr.net/npm/vega-tooltip@0.35.2/build/vega-tooltip.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
  <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/dompurify@3/dist/purify.min.js"></script>
  <script src="./app.js"></script>
</body>
</html>
```

**Arquivo `frontend/styles.css`** (literal):

```css
/* ---------- Tokens ---------- */
:root {
  color-scheme: light;
  --bg: #faf9f5;
  --sidebar: #f5f4ed;
  --surface: #ffffff;
  --surface-2: #f0eee6;
  --hover: rgba(31, 30, 29, 0.06);
  --border: #e6e3d8;
  --border-strong: #d6d2c4;
  --text: #1f1e1d;
  --muted: #73726c;
  --accent: #c96442;
  --accent-hover: #b5563a;
  --accent-soft: rgba(201, 100, 66, 0.12);
  --on-accent: #ffffff;
  --ok: #3f7a52;
  --warn: #b7791f;
  --danger: #b4443c;
  --shadow: 0 1px 2px rgba(31, 30, 29, 0.05), 0 4px 18px rgba(31, 30, 29, 0.06);
  --shadow-lg: 0 8px 40px rgba(31, 30, 29, 0.16);
  --sans: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  --serif: ui-serif, "Iowan Old Style", "Palatino Linotype", Palatino, Georgia, "Times New Roman", serif;
  --mono: ui-monospace, "SF Mono", "Cascadia Mono", Consolas, monospace;
  --sidebar-w: 288px;
  --panel-w: 460px;
  --col: 760px;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    color-scheme: dark;
    --bg: #262624;
    --sidebar: #1f1e1d;
    --surface: #30302e;
    --surface-2: #3a3a37;
    --hover: rgba(250, 249, 245, 0.07);
    --border: #3d3d3a;
    --border-strong: #53524d;
    --text: #faf9f5;
    --muted: #a6a39a;
    --accent: #d97757;
    --accent-hover: #e68a6b;
    --accent-soft: rgba(217, 119, 87, 0.16);
    --ok: #7bc08f;
    --warn: #e0b35a;
    --danger: #e0786f;
    --shadow: 0 1px 2px rgba(0, 0, 0, 0.3), 0 4px 18px rgba(0, 0, 0, 0.25);
    --shadow-lg: 0 8px 40px rgba(0, 0, 0, 0.5);
  }
}

:root[data-theme="dark"] {
  color-scheme: dark;
  --bg: #262624;
  --sidebar: #1f1e1d;
  --surface: #30302e;
  --surface-2: #3a3a37;
  --hover: rgba(250, 249, 245, 0.07);
  --border: #3d3d3a;
  --border-strong: #53524d;
  --text: #faf9f5;
  --muted: #a6a39a;
  --accent: #d97757;
  --accent-hover: #e68a6b;
  --accent-soft: rgba(217, 119, 87, 0.16);
  --ok: #7bc08f;
  --warn: #e0b35a;
  --danger: #e0786f;
  --shadow: 0 1px 2px rgba(0, 0, 0, 0.3), 0 4px 18px rgba(0, 0, 0, 0.25);
  --shadow-lg: 0 8px 40px rgba(0, 0, 0, 0.5);
}

/* ---------- Base ---------- */
* { box-sizing: border-box; }
html, body { height: 100%; }
body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font-family: var(--sans);
  font-size: 15px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
button, input, textarea { font: inherit; color: inherit; }
button { cursor: pointer; }
[hidden] { display: none !important; }
:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
svg { display: block; flex: none; }
::selection { background: var(--accent-soft); }

/* ---------- Shell ---------- */
.app {
  height: 100dvh;
  display: grid;
  grid-template-columns: var(--sidebar-w) minmax(0, 1fr) 0;
  transition: grid-template-columns 0.22s ease;
  overflow: hidden;
}
.app[data-sidebar="closed"] { grid-template-columns: 0 minmax(0, 1fr) 0; }
.app[data-panel="open"] { grid-template-columns: var(--sidebar-w) minmax(0, 1fr) var(--panel-w); }
.app[data-sidebar="closed"][data-panel="open"] { grid-template-columns: 0 minmax(0, 1fr) var(--panel-w); }

/* ---------- Sidebar ---------- */
.sidebar {
  background: var(--sidebar);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.app[data-sidebar="closed"] .sidebar { visibility: hidden; border-right-color: transparent; }
.sidebar > * { width: var(--sidebar-w); }
.sidebar-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 14px 6px 16px; }
.brand { display: inline-flex; align-items: center; gap: 10px; color: var(--text); text-decoration: none; }
.brand-mark { color: var(--accent); width: 26px; height: 26px; }
.brand-mark svg { width: 26px; height: 26px; }
.brand-name { font-family: var(--serif); font-size: 21px; letter-spacing: -0.01em; }

.new-chat {
  margin: 10px 12px 6px;
  width: calc(var(--sidebar-w) - 24px);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--accent);
  font-weight: 600;
  transition: background 0.15s;
}
.new-chat:hover { background: var(--accent-soft); }
.new-chat [data-icon] { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; background: var(--accent); color: var(--on-accent); }
.new-chat [data-icon] svg { width: 14px; height: 14px; }

.sidebar-body { flex: 1; min-height: 0; overflow-y: auto; padding: 8px 8px 8px 12px; scrollbar-width: thin; }
.sidebar-label { padding: 10px 8px 6px; color: var(--muted); font-size: 12px; font-weight: 600; letter-spacing: 0.02em; }
.conversation-list { display: flex; flex-direction: column; gap: 1px; }
.conversation-item {
  position: relative;
  display: block;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  transition: background 0.12s;
}
.conversation-item:hover { background: var(--hover); }
.conversation-item.active { background: var(--surface-2); }
.conversation-empty { padding: 8px 10px; color: var(--muted); font-size: 13px; }

.sidebar-foot { padding: 10px 12px 14px; border-top: 1px solid var(--border); }
.user-chip {
  width: calc(var(--sidebar-w) - 24px);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  color: var(--muted);
}
.user-chip:hover { background: var(--hover); }
.user-chip-text { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.25; }
.user-chip-text strong { color: var(--text); font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-chip-text small { font-size: 12px; }
.user-chip [data-icon="gear"] svg { width: 18px; height: 18px; }
.avatar-sm {
  width: 32px; height: 32px; border-radius: 50%;
  display: grid; place-items: center;
  background: var(--text); color: var(--bg);
  font-weight: 600; font-size: 13px; text-transform: uppercase;
}

/* ---------- Buttons ---------- */
.icon-btn {
  width: 34px; height: 34px;
  display: grid; place-items: center;
  border: 0; border-radius: 8px;
  background: transparent; color: var(--muted);
  transition: background 0.12s, color 0.12s;
}
.icon-btn:hover { background: var(--hover); color: var(--text); }
.icon-btn svg { width: 20px; height: 20px; }
.only-collapsed { display: none; }
.app[data-sidebar="closed"] .only-collapsed { display: grid; }

.panel-btn {
  display: inline-flex; align-items: center; gap: 8px;
  height: 34px; padding: 0 12px;
  border: 1px solid var(--border-strong); border-radius: 9px;
  background: transparent; color: var(--text); font-size: 14px;
  transition: background 0.12s;
}
.panel-btn:hover { background: var(--hover); }
.panel-btn[aria-expanded="true"] { background: var(--surface-2); }
.panel-btn svg { width: 17px; height: 17px; }
.count {
  min-width: 20px; height: 20px; padding: 0 6px;
  display: inline-grid; place-items: center;
  border-radius: 10px; background: var(--accent); color: var(--on-accent);
  font-size: 12px; font-weight: 600;
}

.btn {
  height: 38px; padding: 0 16px;
  border: 1px solid var(--border-strong); border-radius: 9px;
  background: transparent; font-weight: 500;
}
.btn:hover { background: var(--hover); }
.btn.primary { background: var(--accent); border-color: var(--accent); color: var(--on-accent); }
.btn.primary:hover { background: var(--accent-hover); border-color: var(--accent-hover); }

/* ---------- Main ---------- */
.main { display: flex; flex-direction: column; min-width: 0; min-height: 0; position: relative; }
.topbar {
  display: flex; align-items: center; gap: 10px;
  min-height: 56px; padding: 10px 16px;
}
.topbar-title {
  margin: 0; font-size: 15px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 46ch;
}
.topbar-spacer { flex: 1; }

.status-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 3px 10px; border-radius: 999px;
  background: var(--surface-2); color: var(--muted);
  font-size: 12px; font-weight: 600;
}
.status-pill::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.status-pill.running { color: var(--accent); background: var(--accent-soft); }
.status-pill.running::before { animation: pulse 1.2s ease-in-out infinite; }
.status-pill.finished { color: var(--ok); }
.status-pill.failed { color: var(--danger); }
.status-pill.cancelled { color: var(--warn); }

.scroller { flex: 1; min-height: 0; overflow-y: auto; scrollbar-width: thin; }
.thread { width: min(var(--col), 100%); margin: 0 auto; padding: 12px 20px 28px; min-height: 100%; display: flex; flex-direction: column; }
.messages { display: flex; flex-direction: column; gap: 28px; }
.messages:has(> .empty) { flex: 1; }

/* ---------- Empty state ---------- */
.empty { margin: auto; padding: 8vh 0 12vh; text-align: center; }
.empty-mark { color: var(--accent); width: 44px; height: 44px; margin: 0 auto 18px; }
.empty-mark svg { width: 44px; height: 44px; }
.empty h2 { margin: 0 0 8px; font-family: var(--serif); font-weight: 400; font-size: clamp(28px, 4.5vw, 38px); letter-spacing: -0.015em; }
.empty p { margin: 0 auto 26px; color: var(--muted); max-width: 46ch; }
.suggestions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
.suggestion {
  padding: 9px 14px; border: 1px solid var(--border-strong); border-radius: 12px;
  background: var(--surface); color: var(--text); font-size: 14px; text-align: left;
  transition: background 0.12s, border-color 0.12s, transform 0.12s;
}
.suggestion:hover { background: var(--surface-2); border-color: var(--accent); transform: translateY(-1px); }

/* ---------- Messages ---------- */
.msg { display: flex; gap: 14px; animation: rise 0.25s ease both; }
.msg.user { justify-content: flex-end; }
.msg.user .bubble {
  max-width: 82%;
  padding: 10px 16px;
  border-radius: 18px;
  background: var(--surface-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.msg.assistant .avatar { color: var(--accent); width: 28px; height: 28px; margin-top: 2px; }
.msg.assistant .avatar svg { width: 28px; height: 28px; }
.msg.assistant .content { flex: 1; min-width: 0; font-family: var(--serif); font-size: 17px; line-height: 1.7; }

/* markdown */
.markdown-text > :first-child { margin-top: 0; }
.markdown-text > :last-child { margin-bottom: 0; }
.markdown-text p, .markdown-text ul, .markdown-text ol { margin: 0 0 1em; }
.markdown-text ul, .markdown-text ol { padding-left: 1.4em; }
.markdown-text li + li { margin-top: 0.3em; }
.markdown-text h1, .markdown-text h2, .markdown-text h3, .markdown-text h4 {
  font-family: var(--serif); font-weight: 600; line-height: 1.3; margin: 1.4em 0 0.5em;
}
.markdown-text h1 { font-size: 1.4em; }
.markdown-text h2 { font-size: 1.22em; }
.markdown-text h3 { font-size: 1.08em; }
.markdown-text h4 { font-size: 1em; }
.markdown-text a { color: var(--accent); }
.markdown-text hr { border: 0; border-top: 1px solid var(--border); margin: 1.4em 0; }
.markdown-text blockquote { margin: 0 0 1em; padding-left: 14px; border-left: 3px solid var(--border-strong); color: var(--muted); }
.markdown-text code { font-family: var(--mono); font-size: 0.85em; padding: 0.15em 0.4em; border-radius: 6px; background: var(--surface-2); }
.markdown-text pre { margin: 0 0 1em; padding: 14px 16px; border-radius: 12px; background: var(--surface-2); overflow-x: auto; font-size: 14px; line-height: 1.55; }
.markdown-text pre code { padding: 0; background: transparent; font-size: inherit; }
.markdown-text table { border-collapse: collapse; margin: 0 0 1em; font-family: var(--sans); font-size: 14px; }
.markdown-text th, .markdown-text td { padding: 8px 12px; border: 1px solid var(--border); text-align: left; }
.markdown-text th { background: var(--surface-2); font-weight: 600; }
.markdown-text + .vega-chart, .vega-chart + .markdown-text, .markdown-text + .data-table, .data-table + .markdown-text { margin-top: 1em; }

/* tables rendered by the backend */
.data-table {
  margin: 1.1em 0;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  font-family: var(--sans);
  overflow: hidden;
}
.data-table h3 { margin: 0; padding: 12px 16px; font-family: var(--sans); font-size: 14px; font-weight: 600; border-bottom: 1px solid var(--border); }
.table-scroll { overflow-x: auto; max-height: 420px; scrollbar-width: thin; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.data-table th, .data-table td { padding: 9px 14px; text-align: left; white-space: nowrap; border-bottom: 1px solid var(--border); }
.data-table th { position: sticky; top: 0; background: var(--surface-2); color: var(--muted); font-weight: 600; font-size: 12px; letter-spacing: 0.02em; }
.data-table tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover td { background: var(--hover); }

.vega-chart {
  width: 100%; min-height: 300px;
  margin: 1.1em 0; padding: 0;
  border: 1px solid var(--border); border-radius: 14px;
  background: var(--surface); font-family: var(--sans);
  overflow: hidden;
}
.vega-chart .vega-embed { width: 100%; }
.vega-chart.failed { color: var(--danger); font-size: 14px; }

/* ---------- Activity (agent progress) ---------- */
.activity-slot { margin-top: 24px; }
.activity { display: flex; gap: 14px; animation: rise 0.25s ease both; }
.activity .avatar { color: var(--accent); width: 28px; height: 28px; margin-top: 2px; }
.activity .avatar svg { width: 28px; height: 28px; }
.activity.running .avatar svg { animation: spin 3.2s linear infinite; }
.activity details { flex: 1; min-width: 0; }
.activity summary {
  list-style: none; cursor: pointer;
  display: inline-flex; align-items: center; gap: 8px;
  padding: 4px 6px 4px 0; color: var(--muted); font-size: 14px;
}
.activity summary::-webkit-details-marker { display: none; }
.activity summary:hover { color: var(--text); }
.activity .chev { width: 16px; height: 16px; transition: transform 0.15s; }
.activity details[open] .chev { transform: rotate(90deg); }
.activity.running .summary-text { background: linear-gradient(90deg, var(--muted) 0%, var(--text) 50%, var(--muted) 100%); background-size: 200% 100%; -webkit-background-clip: text; background-clip: text; color: transparent; animation: shimmer 2s linear infinite; }
.activity-logs {
  margin: 8px 0 0 7px; padding: 2px 0 2px 16px;
  border-left: 2px solid var(--border);
  display: flex; flex-direction: column; gap: 10px;
  max-height: 300px; overflow-y: auto; scrollbar-width: thin;
}
.log-entry { color: var(--muted); font-size: 13.5px; line-height: 1.45; }
.log-entry time { display: block; font-size: 11.5px; opacity: 0.75; font-variant-numeric: tabular-nums; }
.log-entry.error { color: var(--danger); }

/* ---------- Composer ---------- */
.composer-wrap { padding: 0 16px 14px; }
.composer {
  width: min(var(--col), 100%); margin: 0 auto;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 22px;
  padding: 14px 14px 10px 18px;
  box-shadow: var(--shadow);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft), var(--shadow); }
.composer textarea {
  display: block; width: 100%; resize: none;
  border: 0; outline: 0; background: transparent;
  font-size: 16px; line-height: 1.5; max-height: 200px; padding: 0;
}
.composer textarea::placeholder { color: var(--muted); }
.composer-row { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
.composer-hint { color: var(--muted); font-size: 12px; }
.send-btn {
  width: 34px; height: 34px; display: grid; place-items: center;
  border: 0; border-radius: 10px;
  background: var(--accent); color: var(--on-accent);
  transition: background 0.12s, opacity 0.12s, transform 0.12s;
}
.send-btn svg { width: 18px; height: 18px; }
.send-btn:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-1px); }
.send-btn:disabled { opacity: 0.35; cursor: default; }
.footnote { margin: 8px 0 0; text-align: center; color: var(--muted); font-size: 12px; }

/* ---------- Artifact panel ---------- */
.panel {
  background: var(--bg);
  border-left: 1px solid var(--border);
  display: flex; flex-direction: column;
  min-width: 0; overflow: hidden;
}
.app[data-panel="closed"] .panel { visibility: hidden; border-left-color: transparent; }
.panel > * { width: var(--panel-w); max-width: 100vw; }
.panel-head { display: flex; align-items: center; gap: 8px; min-height: 56px; padding: 10px 14px 10px 16px; border-bottom: 1px solid var(--border); }
.panel-head strong { font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel-body { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; scrollbar-width: thin; }
.panel-empty { color: var(--muted); font-size: 14px; text-align: center; padding: 40px 12px; }
.artifact-list { display: flex; flex-direction: column; gap: 10px; }
.artifact-card {
  text-align: left; width: 100%;
  padding: 12px 14px; border: 1px solid var(--border); border-radius: 14px;
  background: var(--surface); transition: border-color 0.12s, transform 0.12s;
}
.artifact-card:hover { border-color: var(--accent); transform: translateY(-1px); }
.artifact-card.superseded { opacity: 0.62; }
.artifact-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 6px; }
.artifact-title { font-weight: 600; font-size: 14.5px; line-height: 1.35; }
.artifact-date { margin-top: 4px; color: var(--muted); font-size: 12px; }
.chip { padding: 2px 8px; border-radius: 6px; background: var(--surface-2); color: var(--muted); font-size: 11.5px; font-weight: 600; letter-spacing: 0.03em; }
.chip.type { background: var(--accent-soft); color: var(--accent); }
.chip.ok { color: var(--ok); }
.chip.warn { color: var(--warn); }
.artifact-detail .artifact-title { font-family: var(--serif); font-size: 20px; margin-bottom: 8px; }
.artifact-detail .content { font-family: var(--serif); font-size: 16px; line-height: 1.65; margin-top: 14px; }

/* ---------- Dialog & toast ---------- */
.dialog {
  width: min(420px, calc(100vw - 32px));
  padding: 24px; border: 1px solid var(--border); border-radius: 18px;
  background: var(--surface); color: var(--text); box-shadow: var(--shadow-lg);
}
.dialog::backdrop { background: rgba(0, 0, 0, 0.38); backdrop-filter: blur(2px); }
.dialog h2 { margin: 0 0 16px; font-family: var(--serif); font-weight: 400; font-size: 24px; }
.dialog label { display: block; margin-bottom: 14px; }
.dialog label span { display: block; margin-bottom: 6px; color: var(--muted); font-size: 13px; font-weight: 600; }
.dialog input { width: 100%; height: 40px; padding: 0 12px; border: 1px solid var(--border-strong); border-radius: 10px; background: var(--bg); }
.dialog input:focus { outline: 0; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }

.toast {
  position: fixed; left: 50%; bottom: 96px; transform: translateX(-50%);
  max-width: min(520px, calc(100vw - 32px));
  padding: 11px 16px; border-radius: 12px;
  background: var(--text); color: var(--bg); font-size: 14px;
  box-shadow: var(--shadow-lg); animation: rise 0.2s ease both; z-index: 50;
}

.scrim { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); z-index: 20; }

/* ---------- Motion ---------- */
@keyframes rise { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
@keyframes pulse { 50% { opacity: 0.35; } }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer { to { background-position: -200% 0; } }
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; scroll-behavior: auto !important; }
}

/* ---------- Small screens ---------- */
@media (max-width: 960px) {
  .app, .app[data-sidebar], .app[data-panel], .app[data-sidebar][data-panel] { grid-template-columns: minmax(0, 1fr); }
  .sidebar, .panel { position: fixed; top: 0; bottom: 0; z-index: 30; box-shadow: var(--shadow-lg); transition: transform 0.22s ease; }
  .sidebar { left: 0; width: var(--sidebar-w); max-width: 86vw; }
  .sidebar > *, .new-chat, .user-chip { width: auto; }
  .panel { right: 0; width: var(--panel-w); max-width: 100vw; border-left-color: var(--border); }
  .app[data-sidebar="closed"] .sidebar { transform: translateX(-100%); }
  .app[data-panel="closed"] .panel { transform: translateX(100%); }
  .app[data-sidebar="closed"] .sidebar, .app[data-panel="closed"] .panel { visibility: visible; }
  .only-collapsed, .app[data-sidebar="closed"] .only-collapsed { display: grid; }
  .panel-btn span:not([data-icon]):not(.count) { display: none; }
  .composer-hint { display: none; }
}
```

**Arquivo `frontend/app.js`** (literal):

````js
const TERMINAL = ["FINISHED", "FAILED", "CANCELLED"];
const ACTIVE = ["NEW", "PLANNING", "RUNNING"];
const POLL_MS = 2200;
const COMPACT_QUERY = "(max-width: 960px)";

const SUGGESTIONS = [
  "Como foi a campanha de cartao em setembro?",
  "Quais segmentos de clientes tem maior propensao de compra?",
  "Compare o desempenho das campanhas do ultimo trimestre"
];

const ICONS = {
  sparkle: `<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 1.5l2.1 6.6 6.4-2.6-3.6 5.9 6.1 3-6.8.9.6 6.9-4.8-4.9-4.8 4.9.6-6.9-6.8-.9 6.1-3-3.6-5.9 6.4 2.6z"/></svg>`,
  plus: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>`,
  sidebar: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/></svg>`,
  gear: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 01-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.9.3h0a1.7 1.7 0 001-1.5V3a2 2 0 014 0v.1a1.7 1.7 0 001 1.5h0a1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.9v0a1.7 1.7 0 001.5 1H21a2 2 0 010 4h-.1a1.7 1.7 0 00-1.5 1z"/></svg>`,
  moon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z"/></svg>`,
  sun: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>`,
  layers: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/></svg>`,
  "arrow-up": `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg>`,
  "arrow-left": `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>`,
  close: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>`,
  chevron: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>`
};

const STATUS_LABELS = {
  PROCESSING: ["Processando", "running"],
  NEW: ["Em execucao", "running"],
  PLANNING: ["Em execucao", "running"],
  RUNNING: ["Em execucao", "running"],
  FINISHED: ["Concluido", "finished"],
  FAILED: ["Falhou", "failed"],
  CANCELLED: ["Cancelado", "cancelled"]
};

const RUN_SUMMARIES = { FINISHED: "Analise concluida", FAILED: "A execucao falhou", CANCELLED: "Execucao cancelada" };

const state = {
  apiBase: localStorage.getItem("hub_api_base") || "http://localhost:8080",
  userId: localStorage.getItem("hub_user_id") || "user-test-1",
  activeConversationId: null,
  activeAgentRunId: null,
  pendingMessageId: null,
  pollTimer: null,
  sending: false,
  renderedIds: [],
  lastMessages: [],
  activity: null,
  activityKey: "",
  activityOpen: false,
  pinned: true,
  artifacts: [],
  selectedArtifactId: null
};

const $ = (id) => document.getElementById(id);
const els = {
  app: $("app"), scrim: $("scrim"), scroller: $("scroller"),
  conversations: $("conversationList"), messages: $("messages"), activity: $("activity"),
  composer: $("composer"), input: $("messageInput"), send: $("sendButton"),
  newChat: $("newChatButton"), title: $("conversationTitle"), status: $("statusPill"),
  sidebarOpen: $("sidebarOpen"), sidebarClose: $("sidebarClose"),
  theme: $("themeButton"), panelButton: $("panelButton"), panelClose: $("panelClose"), panelBack: $("panelBack"),
  panelTitle: $("panelTitle"), panelBody: $("panelBody"), artifactCount: $("artifactCount"),
  settingsButton: $("settingsButton"), settingsDialog: $("settingsDialog"), settingsForm: $("settingsForm"), settingsCancel: $("settingsCancel"),
  userIdInput: $("userIdInput"), apiBaseInput: $("apiBaseInput"), userLabel: $("userLabel"), userAvatar: $("userAvatar")
};

function init() {
  applyIcons(document);
  initTheme();
  setSidebar(!window.matchMedia(COMPACT_QUERY).matches, true);
  renderUser();
  bindEvents();
  renderEmpty();
  loadConversations();
  const params = new URLSearchParams(window.location.search);
  if (params.get("c")) openConversation(params.get("c")).then(() => { if (params.get("panel")) setPanel(true); });
}

/* ---------- shell ---------- */

function bindEvents() {
  els.newChat.addEventListener("click", newConversation);
  els.sidebarOpen.addEventListener("click", () => setSidebar(true));
  els.sidebarClose.addEventListener("click", () => setSidebar(false));
  els.panelButton.addEventListener("click", () => setPanel(els.app.dataset.panel !== "open"));
  els.panelClose.addEventListener("click", () => setPanel(false));
  els.panelBack.addEventListener("click", () => { state.selectedArtifactId = null; renderPanel(); });
  els.scrim.addEventListener("click", () => { setSidebar(false, true); setPanel(false, true); });
  els.theme.addEventListener("click", toggleTheme);

  els.scroller.addEventListener("scroll", () => {
    state.pinned = els.scroller.scrollHeight - els.scroller.scrollTop - els.scroller.clientHeight < 120;
  }, { passive: true });
  new ResizeObserver(() => { if (state.pinned) els.scroller.scrollTop = els.scroller.scrollHeight; }).observe($("thread"));

  els.composer.addEventListener("submit", async (event) => { event.preventDefault(); await sendMessage(); });
  els.input.addEventListener("input", onComposerInput);
  els.input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); els.composer.requestSubmit(); }
  });

  els.settingsButton.addEventListener("click", () => {
    els.userIdInput.value = state.userId;
    els.apiBaseInput.value = state.apiBase;
    els.settingsDialog.showModal();
  });
  els.settingsCancel.addEventListener("click", () => els.settingsDialog.close());
  els.settingsForm.addEventListener("submit", saveSettings);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && els.app.dataset.panel === "open" && !els.settingsDialog.open) setPanel(false);
  });
}

function setSidebar(open, fromScrim = false) {
  els.app.dataset.sidebar = open ? "open" : "closed";
  syncScrim();
  if (!open && !fromScrim) els.input.focus({ preventScroll: true });
}

function setPanel(open, fromScrim = false) {
  els.app.dataset.panel = open ? "open" : "closed";
  els.panelButton.setAttribute("aria-expanded", String(open));
  if (open) {
    if (window.matchMedia(COMPACT_QUERY).matches) setSidebar(false);
    loadArtifacts();
  }
  syncScrim();
}

function syncScrim() {
  const compact = window.matchMedia(COMPACT_QUERY).matches;
  els.scrim.hidden = !(compact && (els.app.dataset.sidebar === "open" || els.app.dataset.panel === "open"));
}

function initTheme() {
  const requested = new URLSearchParams(window.location.search).get("theme");
  const stored = requested || localStorage.getItem("hub_theme");
  if (stored === "light" || stored === "dark") document.documentElement.dataset.theme = stored;
  renderThemeIcon();
}

function isDark() {
  const forced = document.documentElement.dataset.theme;
  return forced ? forced === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function toggleTheme() {
  const next = isDark() ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  localStorage.setItem("hub_theme", next);
  renderThemeIcon();
  state.renderedIds = [];
  renderMessages(state.lastMessages);
  renderPanel();
}

function renderThemeIcon() {
  els.theme.dataset.icon = isDark() ? "sun" : "moon";
  applyIcons(els.theme.parentElement);
}

function applyIcons(root) {
  root.querySelectorAll("[data-icon]").forEach((node) => {
    const icon = ICONS[node.dataset.icon];
    if (icon) node.innerHTML = icon;
  });
}

function renderUser() {
  els.userLabel.textContent = state.userId;
  els.userAvatar.textContent = (state.userId.trim()[0] || "U");
}

function saveSettings() {
  state.userId = els.userIdInput.value.trim() || "user-test-1";
  state.apiBase = trimSlash(els.apiBaseInput.value.trim() || "http://localhost:8080");
  localStorage.setItem("hub_user_id", state.userId);
  localStorage.setItem("hub_api_base", state.apiBase);
  renderUser();
  newConversation();
  loadConversations();
}

function onComposerInput() {
  els.input.style.height = "auto";
  els.input.style.height = `${Math.min(200, els.input.scrollHeight)}px`;
  els.send.disabled = state.sending || !els.input.value.trim();
}

/* ---------- conversations ---------- */

function newConversation() {
  stopPolling();
  state.activeConversationId = null;
  state.activeAgentRunId = null;
  state.pendingMessageId = null;
  state.artifacts = [];
  state.selectedArtifactId = null;
  syncUrl();
  renderEmpty();
  renderArtifactCount();
  renderPanel();
  markActiveConversation();
  if (window.matchMedia(COMPACT_QUERY).matches) setSidebar(false, true);
  els.input.focus();
}

async function sendMessage() {
  const message = els.input.value.trim();
  if (!message || state.sending) return;
  state.sending = true;
  els.send.disabled = true;
  appendLocalMessage(message);
  els.input.value = "";
  els.input.style.height = "auto";
  try {
    const response = state.activeConversationId
      ? await request(`/conversations/${state.activeConversationId}`, { method: "POST", body: { message } })
      : await request("/conversation", { method: "POST", body: { message } });
    state.activeConversationId = response.conversation_id;
    syncUrl();
    state.pendingMessageId = response.message_id;
    state.activeAgentRunId = null;
    setStatus("PROCESSING");
    setAgentRun(null, true);
    await loadConversations();
    startPolling();
  } catch (error) {
    showToast(error.message);
    setAgentRun(null, false);
    setStatus("IDLE");
  } finally {
    state.sending = false;
    onComposerInput();
    els.input.focus();
  }
}

async function loadConversations() {
  try {
    const conversations = await request("/conversations");
    els.conversations.innerHTML = "";
    if (!Array.isArray(conversations) || conversations.length === 0) {
      els.conversations.append(textNode("div", "conversation-empty", "Nenhuma conversa ainda."));
      return;
    }
    conversations.forEach((conversation) => {
      const button = textNode("button", "conversation-item", conversation.chat_last_message || "Conversa sem titulo");
      button.type = "button";
      button.dataset.id = conversation.id;
      button.title = conversation.chat_last_message || "";
      button.addEventListener("click", () => openConversation(conversation.id));
      els.conversations.appendChild(button);
    });
    markActiveConversation();
  } catch (error) {
    els.conversations.innerHTML = "";
    els.conversations.append(textNode("div", "conversation-empty", error.message));
  }
}

/** Keeps the open conversation in the address bar so a refresh or a shared link reopens it. */
function syncUrl() {
  try {
    const url = new URL(window.location.href);
    if (state.activeConversationId) url.searchParams.set("c", state.activeConversationId); else url.searchParams.delete("c");
    url.searchParams.delete("panel");
    window.history.replaceState(null, "", url);
  } catch (error) {
    // some file:// contexts forbid history updates; the link is a convenience only
  }
}

function markActiveConversation() {
  els.conversations.querySelectorAll(".conversation-item").forEach((item) => {
    item.classList.toggle("active", item.dataset.id === state.activeConversationId);
  });
}

async function openConversation(conversationId) {
  try {
    await loadConversation(conversationId);
    if (window.matchMedia(COMPACT_QUERY).matches) setSidebar(false, true);
  } catch (error) {
    showToast(error.message);
  }
}

async function loadConversation(conversationId) {
  stopPolling();
  state.activeConversationId = conversationId;
  state.activeAgentRunId = null;
  state.pendingMessageId = null;
  state.selectedArtifactId = null;
  state.renderedIds = [];
  syncUrl();
  markActiveConversation();
  setAgentRun(null, false);
  setStatus("IDLE");
  await refreshMessages();
  const runs = await request(`/conversations/${conversationId}/agent-runs`);
  if (conversationId !== state.activeConversationId) return;
  const active = runs.find((run) => ACTIVE.includes(run.status)) || runs.at(-1);
  if (active) state.activeAgentRunId = active.id;
  if (state.activeAgentRunId) await pollAgentRun();
  if (!runs.some((run) => ACTIVE.includes(run.status))) setStatus("IDLE");
  await loadArtifacts();
  if (runs.some((run) => ACTIVE.includes(run.status))) startPolling();
}

async function refreshMessages() {
  const id = state.activeConversationId;
  if (!id) return null;
  const conversation = await request(`/conversations/${id}`);
  if (id !== state.activeConversationId) return null;
  state.lastMessages = conversation.messages || [];
  renderMessages(state.lastMessages);
  els.title.textContent = titleFrom(state.lastMessages);
  return conversation;
}

async function pollConversation() {
  if (!state.activeConversationId) return;
  const conversationId = state.activeConversationId;
  const conversation = await refreshMessages();
  if (!conversation || conversationId !== state.activeConversationId) return;

  if (state.pendingMessageId && !state.activeAgentRunId) {
    const runs = await request(`/conversations/${conversationId}/agent-runs?triggering_message_id=${encodeURIComponent(state.pendingMessageId)}`);
    if (conversationId !== state.activeConversationId) return;
    if (runs.length) {
      state.activeAgentRunId = runs.at(-1).id;
    } else if (hasAssistantResponse(state.lastMessages, state.pendingMessageId)) {
      state.pendingMessageId = null;
      setStatus("IDLE");
      setAgentRun(null, false);
      stopPolling();
      loadConversations();
    }
  }
  if (state.activeAgentRunId) await pollAgentRun();
}

async function pollAgentRun() {
  const conversationId = state.activeConversationId;
  const runId = state.activeAgentRunId;
  if (!conversationId || !runId) return;
  const run = await request(`/conversations/${conversationId}/agent-runs/${runId}`);
  if (conversationId !== state.activeConversationId || runId !== state.activeAgentRunId) return;
  setAgentRun(run);
  setStatus(run.status);
  if (TERMINAL.includes(run.status)) {
    state.pendingMessageId = null;
    stopPolling();
    // the final answer is written right after the run turns terminal, so refresh once more
    window.setTimeout(() => finishRun(conversationId), 700);
  }
}

async function finishRun(conversationId) {
  if (conversationId !== state.activeConversationId) return;
  try {
    await refreshMessages();
    await loadArtifacts();
    await loadConversations();
  } catch (error) {
    showToast(error.message);
  }
}

function startPolling() {
  stopPolling();
  if (!state.activeConversationId) return;
  state.pollTimer = window.setInterval(() => pollConversation().catch((error) => showToast(error.message)), POLL_MS);
}

function stopPolling() {
  if (state.pollTimer) window.clearInterval(state.pollTimer);
  state.pollTimer = null;
}

function hasAssistantResponse(messages, userMessageId) {
  const index = messages.findIndex((message) => message.id === userMessageId);
  return index >= 0 && messages.slice(index + 1).some((message) => message.role === "assistant");
}

/* ---------- messages ---------- */

function renderEmpty() {
  state.renderedIds = [];
  state.lastMessages = [];
  els.title.textContent = "Nova conversa";
  els.messages.innerHTML = "";
  const empty = textNode("div", "empty", "");
  empty.innerHTML = `<div class="empty-mark">${ICONS.sparkle}</div><h2>Ola! Como posso ajudar hoje?</h2><p>Converse livremente. O Hub aciona os especialistas de dados apenas quando faz sentido.</p>`;
  const list = textNode("div", "suggestions", "");
  SUGGESTIONS.forEach((suggestion) => {
    const chip = textNode("button", "suggestion", suggestion);
    chip.type = "button";
    chip.addEventListener("click", () => { els.input.value = suggestion; onComposerInput(); els.composer.requestSubmit(); });
    list.append(chip);
  });
  empty.append(list);
  els.messages.append(empty);
  setAgentRun(null, false);
  setStatus("IDLE");
}

function renderMessages(messages) {
  els.messages.querySelectorAll("[data-local]").forEach((node) => node.remove());
  if (!messages.length) {
    if (!els.messages.querySelector(".empty")) renderEmpty();
    return;
  }
  const ids = messages.map((message) => message.id);
  const unchanged = state.renderedIds.length <= ids.length && state.renderedIds.every((id, index) => id === ids[index]);
  if (!unchanged || els.messages.querySelector(".empty")) {
    els.messages.innerHTML = "";
    state.renderedIds = [];
  }
  const fresh = messages.slice(state.renderedIds.length);
  fresh.forEach(appendMessage);
  state.renderedIds = ids;
  if (fresh.length) scrollToBottom(state.renderedIds.length === fresh.length);
}

function appendLocalMessage(content) {
  if (els.messages.querySelector(".empty")) { els.messages.innerHTML = ""; state.renderedIds = []; }
  const node = buildMessage("user", content);
  node.dataset.local = "true";
  els.messages.append(node);
  scrollToBottom(true);
}

function appendMessage(message) {
  els.messages.append(buildMessage(message.role === "ai" ? "assistant" : message.role, message.content || ""));
}

function buildMessage(role, content) {
  const node = document.createElement("article");
  node.className = `msg ${role}`;
  if (role === "user") {
    node.append(textNode("div", "bubble", content));
    return node;
  }
  const avatar = textNode("div", "avatar", "");
  avatar.innerHTML = ICONS.sparkle;
  const body = textNode("div", "content", "");
  body.append(renderMarkdownContent(content));
  node.append(avatar, body);
  return node;
}

/** Follows new content while the reader is at the bottom; stops following once they scroll up. */
function scrollToBottom(force = false) {
  if (force) state.pinned = true;
  if (state.pinned) window.requestAnimationFrame(() => { els.scroller.scrollTop = els.scroller.scrollHeight; });
}

function titleFrom(messages) {
  const first = messages.find((message) => message.role === "user");
  if (!first) return "Conversa";
  const text = first.content.replace(/\s+/g, " ").trim();
  return text.length > 70 ? `${text.slice(0, 70)}...` : text;
}

/* ---------- agent activity ---------- */

function setAgentRun(run, thinking = Boolean(run)) {
  state.activity = run ? { run } : (thinking ? { run: null } : null);
  renderActivity();
}

function renderActivity() {
  const view = state.activity;
  if (!view) {
    state.activityKey = "";
    els.activity.innerHTML = "";
    return;
  }
  const run = view.run;
  const running = !run || ACTIVE.includes(run.status);
  const logs = (run?.logs || []).slice(-80);
  const summary = running ? (run?.current_message || "Pensando...") : (RUN_SUMMARIES[run.status] || run.status);
  const key = JSON.stringify([running, summary, logs.length, logs.at(-1)?.message]);
  if (key === state.activityKey) return;
  state.activityKey = key;

  const root = textNode("article", `activity${running ? " running" : ""}`, "");
  const avatar = textNode("div", "avatar", "");
  avatar.innerHTML = ICONS.sparkle;
  const details = document.createElement("details");
  details.open = state.activityOpen && logs.length > 0;
  details.addEventListener("toggle", () => { state.activityOpen = details.open; });
  const head = document.createElement("summary");
  const chevron = textNode("span", "chev", "");
  chevron.innerHTML = ICONS.chevron;
  head.append(chevron, textNode("span", "summary-text", summary));
  details.append(head);
  if (logs.length) {
    const list = textNode("div", "activity-logs", "");
    logs.forEach((log) => {
      const entry = textNode("div", `log-entry${log.level === "ERROR" ? " error" : ""}`, "");
      entry.append(textNode("time", "", formatDate(log.at)), document.createTextNode(log.message || ""));
      list.append(entry);
    });
    details.append(list);
  }
  root.append(avatar, details);
  els.activity.replaceChildren(root);
  scrollToBottom();
}

function setStatus(status) {
  const entry = STATUS_LABELS[status];
  els.status.hidden = !entry;
  els.status.className = `status-pill${entry ? ` ${entry[1]}` : ""}`;
  els.status.textContent = entry ? entry[0] : "";
}

/* ---------- artifacts ---------- */

async function loadArtifacts() {
  const id = state.activeConversationId;
  if (!id) { state.artifacts = []; renderArtifactCount(); renderPanel(); return; }
  try {
    const artifacts = await request(`/conversations/${id}/artifacts`);
    if (id !== state.activeConversationId) return;
    state.artifacts = Array.isArray(artifacts) ? artifacts : [];
  } catch (error) {
    state.artifacts = [];
  }
  renderArtifactCount();
  renderPanel();
}

function renderArtifactCount() {
  const count = state.artifacts.length;
  els.artifactCount.hidden = count === 0;
  els.artifactCount.textContent = String(count);
}

function renderPanel() {
  const selected = state.artifacts.find((artifact) => artifact.id === state.selectedArtifactId);
  els.panelBack.hidden = !selected;
  els.panelTitle.textContent = selected ? selected.type : "Artefatos";
  els.panelBody.innerHTML = "";
  if (selected) return els.panelBody.append(renderArtifactDetail(selected));
  if (!state.artifacts.length) {
    els.panelBody.append(textNode("div", "panel-empty", "Os resultados produzidos pelos agentes (insights, briefings) aparecem aqui."));
    return;
  }
  const list = textNode("div", "artifact-list", "");
  state.artifacts.forEach((artifact) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = `artifact-card${artifact.status === "SUPERSEDED" ? " superseded" : ""}`;
    card.append(artifactMeta(artifact), textNode("div", "artifact-title", artifact.title || artifact.type), textNode("div", "artifact-date", formatDate(artifact.created_at, true)));
    card.addEventListener("click", () => { state.selectedArtifactId = artifact.id; renderPanel(); });
    list.append(card);
  });
  els.panelBody.append(list);
}

function artifactMeta(artifact) {
  const meta = textNode("div", "artifact-meta", "");
  meta.append(textNode("span", "chip type", artifact.type), textNode("span", "chip", `v${artifact.version}`));
  meta.append(artifact.status === "ACTIVE" ? textNode("span", "chip ok", "Ativo") : textNode("span", "chip", "Substituido"));
  if (artifact.stale) meta.append(textNode("span", "chip warn", "Desatualizado"));
  return meta;
}

function renderArtifactDetail(artifact) {
  const root = textNode("div", "artifact-detail", "");
  const content = textNode("div", "content", "");
  content.append(renderMarkdownContent(artifact.rendered_content || artifact.content || ""));
  root.append(artifactMeta(artifact), textNode("div", "artifact-title", artifact.title || artifact.type),
    textNode("div", "artifact-date", formatDate(artifact.created_at, true)), content);
  return root;
}

/* ---------- http ---------- */

async function request(path, options = {}) {
  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || "GET",
    headers: { "Content-Type": "application/json", Authorization: state.userId },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch (error) { body = { message: text.slice(0, 200) }; }
  if (!response.ok) throw new Error(body?.message || body?.error || `Erro HTTP ${response.status}`);
  return body;
}

/* ---------- markdown and charts ---------- */

function renderMarkdownContent(value) {
  const fragment = document.createDocumentFragment();
  const chartPattern = /```vega-lite\s*\n([\s\S]*?)```/g;
  let index = 0;
  let match;
  while ((match = chartPattern.exec(value)) !== null) {
    appendMarkdownText(fragment, value.slice(index, match.index));
    appendVegaChart(fragment, match[1]);
    index = chartPattern.lastIndex;
  }
  appendMarkdownText(fragment, value.slice(index));
  return fragment;
}

function appendMarkdownText(target, value) {
  if (!value.trim()) return;
  const text = textNode("div", "markdown-text", "");
  if (typeof window.marked?.parse === "function" && typeof window.DOMPurify?.sanitize === "function") {
    text.innerHTML = window.DOMPurify.sanitize(window.marked.parse(value));
    // tables stored before the provider-neutral class name keep their styling
    text.querySelectorAll(".genie-table").forEach((node) => node.classList.replace("genie-table", "data-table"));
  } else {
    text.innerHTML = escapeHtml(value).replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>").replace(/\n/g, "<br>");
  }
  target.append(text);
}

function appendVegaChart(target, source) {
  const chart = textNode("div", "vega-chart", "");
  target.append(chart);
  try {
    const spec = JSON.parse(source.trim());
    if (typeof window.vegaEmbed !== "function") throw new Error("Biblioteca Vega-Lite indisponivel.");
    const dark = isDark();
    const height = Number.isFinite(spec.height) ? Math.min(spec.height, 460) : 380;
    // padding lives inside the view: "width: container" measures the element including its own padding
    const responsiveSpec = { padding: 16, ...spec, background: "transparent", width: "container", height, autosize: { type: "fit", contains: "padding", resize: true } };
    const tooltip = typeof window.vegaTooltip?.Handler === "function"
      ? new window.vegaTooltip.Handler({ theme: "dark" }).call
      : true;
    // "width: container" measures the element, so embed only once it is attached to the page
    window.requestAnimationFrame(() => window.vegaEmbed(chart, responsiveSpec, { actions: false, renderer: "svg", tooltip, theme: dark ? "dark" : undefined })
      .catch((error) => renderChartError(chart, error.message)));
  } catch (error) {
    renderChartError(chart, error.message);
  }
}

function renderChartError(chart, message) {
  chart.classList.add("failed");
  chart.textContent = `Nao foi possivel exibir este grafico: ${message}`;
}

/* ---------- helpers ---------- */

function textNode(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text) node.textContent = text;
  return node;
}

function formatDate(value, long = false) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const options = long
    ? { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }
    : { hour: "2-digit", minute: "2-digit", second: "2-digit" };
  return new Intl.DateTimeFormat("pt-BR", options).format(date);
}

function trimSlash(value) { return value.replace(/\/+$/, ""); }
function escapeHtml(value) { return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;"); }
function showToast(message) {
  const toast = textNode("div", "toast", message);
  document.body.appendChild(toast);
  window.setTimeout(() => toast.remove(), 4200);
}

init();
````

**Arquivo `frontend/README.md`** (literal):

```markdown
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
```

## Ordem de implementação sugerida

`index.html` → `styles.css` → `app.js` → `README.md`; validar contra a API da spec 05.
