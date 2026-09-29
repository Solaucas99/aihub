const state = {
  apiBase: localStorage.getItem("hub_api_base") || "http://localhost:8080",
  userId: localStorage.getItem("hub_user_id") || "user-test-1",
  activeConversationId: null,
  activeAgentRunId: null,
  pendingMessageId: null,
  pollTimer: null,
  sending: false
};

const $ = (id) => document.getElementById(id);
const els = {
  userId: $("userIdInput"), apiBase: $("apiBaseInput"), conversations: $("conversationList"), messages: $("messages"),
  composer: $("composer"), messageInput: $("messageInput"), sendButton: $("sendButton"), newChat: $("newChatButton"),
  refresh: $("refreshButton"), title: $("conversationTitle"), subtitle: $("conversationSubtitle"), status: $("statusBadge"),
  runId: $("workflowIdLabel"), executionStatus: $("executionStatus"), currentMessage: $("currentMessage"), logs: $("workflowLogs")
};

function init() {
  els.userId.value = state.userId;
  els.apiBase.value = state.apiBase;
  bindEvents();
  renderEmpty();
  loadConversations();
}

function bindEvents() {
  els.userId.addEventListener("change", () => { state.userId = els.userId.value.trim() || "user-test-1"; localStorage.setItem("hub_user_id", state.userId); loadConversations(); });
  els.apiBase.addEventListener("change", () => { state.apiBase = trimSlash(els.apiBase.value.trim() || "http://localhost:8080"); els.apiBase.value = state.apiBase; localStorage.setItem("hub_api_base", state.apiBase); loadConversations(); });
  els.newChat.addEventListener("click", () => { stopPolling(); state.activeConversationId = null; state.activeAgentRunId = null; state.pendingMessageId = null; renderEmpty(); setAgentRun(null); els.messageInput.focus(); });
  els.refresh.addEventListener("click", () => { loadConversations(); if (state.activeConversationId) pollConversation(); });
  els.composer.addEventListener("submit", async (event) => { event.preventDefault(); await sendMessage(); });
  els.messageInput.addEventListener("input", () => { els.messageInput.style.height = "auto"; els.messageInput.style.height = `${Math.min(180, els.messageInput.scrollHeight)}px`; });
  els.messageInput.addEventListener("keydown", (event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); els.composer.requestSubmit(); } });
}

async function sendMessage() {
  const message = els.messageInput.value.trim();
  if (!message || state.sending) return;
  state.sending = true;
  els.sendButton.disabled = true;
  appendLocalMessage("user", message);
  els.messageInput.value = "";
  els.messageInput.style.height = "auto";
  try {
    const response = state.activeConversationId
      ? await request(`/conversations/${state.activeConversationId}`, { method: "POST", body: { message } })
      : await request("/conversation", { method: "POST", body: { message } });
    state.activeConversationId = response.conversation_id;
    state.pendingMessageId = response.message_id;
    state.activeAgentRunId = null;
    setStatus("PROCESSING");
    setAgentRun(null);
    await loadConversations();
    startPolling();
  } catch (error) {
    showToast(error.message);
  } finally {
    state.sending = false;
    els.sendButton.disabled = false;
    els.messageInput.focus();
  }
}

async function loadConversations() {
  try {
    const conversations = await request("/conversations");
    els.conversations.innerHTML = "";
    if (!Array.isArray(conversations) || conversations.length === 0) {
      els.conversations.innerHTML = `<div class="conversation-meta">Nenhuma conversa ainda.</div>`;
      return;
    }
    conversations.forEach((conversation) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `conversation-item${conversation.id === state.activeConversationId ? " active" : ""}`;
      button.innerHTML = `<span class="conversation-title">${escapeHtml(conversation.chat_last_message || "Conversa sem titulo")}</span><span class="conversation-meta">${formatDate(conversation.updated_at)}</span>`;
      button.addEventListener("click", () => loadConversation(conversation.id));
      els.conversations.appendChild(button);
    });
  } catch (error) {
    els.conversations.innerHTML = `<div class="conversation-meta">${escapeHtml(error.message)}</div>`;
  }
}

async function loadConversation(conversationId) {
  stopPolling();
  state.activeConversationId = conversationId;
  state.activeAgentRunId = null;
  state.pendingMessageId = null;
  await pollConversation();
  const runs = await request(`/conversations/${conversationId}/agent-runs`);
  const active = runs.find((run) => ["NEW", "PLANNING", "RUNNING"].includes(run.status)) || runs.at(-1);
  if (active) state.activeAgentRunId = active.id;
  if (state.activeAgentRunId) await pollAgentRun();
  startPolling();
}

async function pollConversation() {
  if (!state.activeConversationId) return;
  const conversation = await request(`/conversations/${state.activeConversationId}`);
  renderMessages(conversation.messages || []);
  els.title.textContent = "Conversa";
  els.subtitle.textContent = conversation.chat_last_message || "Hub de IA";

  if (state.pendingMessageId && !state.activeAgentRunId) {
    const runs = await request(`/conversations/${state.activeConversationId}/agent-runs?triggering_message_id=${encodeURIComponent(state.pendingMessageId)}`);
    if (runs.length) {
      state.activeAgentRunId = runs.at(-1).id;
    } else if (hasAssistantResponse(conversation.messages || [], state.pendingMessageId)) {
      state.pendingMessageId = null;
      setStatus("IDLE");
      setAgentRun(null);
      stopPolling();
    }
  }
  if (state.activeAgentRunId) await pollAgentRun();
}

async function pollAgentRun() {
  if (!state.activeConversationId || !state.activeAgentRunId) return;
  const run = await request(`/conversations/${state.activeConversationId}/agent-runs/${state.activeAgentRunId}`);
  setAgentRun(run);
  setStatus(run.status);
  if (["FINISHED", "FAILED", "CANCELLED"].includes(run.status)) {
    state.pendingMessageId = null;
    stopPolling();
  }
}

function startPolling() {
  stopPolling();
  if (!state.activeConversationId) return;
  state.pollTimer = window.setInterval(() => pollConversation().catch((error) => showToast(error.message)), 2200);
}

function stopPolling() {
  if (state.pollTimer) window.clearInterval(state.pollTimer);
  state.pollTimer = null;
}

function hasAssistantResponse(messages, userMessageId) {
  const index = messages.findIndex((message) => message.id === userMessageId);
  return index >= 0 && messages.slice(index + 1).some((message) => message.role === "assistant");
}

function renderEmpty() {
  els.title.textContent = "Nova conversa";
  els.subtitle.textContent = "Envie uma mensagem para iniciar uma conversa.";
  els.messages.innerHTML = `<div class="empty-state"><h2>Hub de IA</h2><p>Converse livremente. O planner seleciona especialistas apenas quando fizer sentido.</p></div>`;
  setStatus("IDLE");
}

function renderMessages(messages) {
  if (!messages.length) return renderEmpty();
  els.messages.innerHTML = "";
  messages.forEach((message) => appendMessage(message.role, message.content, message.created_at));
  els.messages.scrollTop = els.messages.scrollHeight;
}

function appendLocalMessage(role, content) {
  if (els.messages.querySelector(".empty-state")) els.messages.innerHTML = "";
  appendMessage(role, content, new Date().toISOString());
  els.messages.scrollTop = els.messages.scrollHeight;
}

function appendMessage(role, content, createdAt) {
  const normalized = role === "ai" ? "assistant" : role;
  const node = document.createElement("article");
  node.className = `message ${normalized}`;
  node.innerHTML = `<div class="message-role">${roleLabel(normalized)} <span class="message-time">${formatDate(createdAt)}</span></div>`;
  const bubble = document.createElement("div");
  bubble.className = "message-bubble";
  bubble.append(renderMarkdownContent(content || ""));
  if (bubble.querySelector(".vega-chart, .genie-table")) node.classList.add("with-visualization");
  node.append(bubble);
  els.messages.appendChild(node);
}

function setAgentRun(run) {
  if (!run) {
    els.runId.textContent = "Sem execucao ativa";
    els.executionStatus.textContent = "Aguardando";
    els.currentMessage.textContent = "Os logs aparecem aqui durante uma execucao.";
    els.logs.innerHTML = "";
    return;
  }
  els.runId.textContent = shortId(run.agent_run_id || state.activeAgentRunId);
  els.executionStatus.textContent = run.execution_status || run.status || "RUNNING";
  els.currentMessage.textContent = run.current_message || "Atualizando execucao.";
  els.logs.innerHTML = "";
  (run.logs || []).slice(-80).forEach((log) => {
    const node = document.createElement("div");
    node.className = "log-entry";
    node.innerHTML = `<time>${formatDate(log.at)}</time>${escapeHtml(log.message || "")}`;
    els.logs.appendChild(node);
  });
}

function setStatus(status) {
  const value = status || "IDLE";
  els.status.textContent = value;
  els.status.className = "status-badge";
  if (["RUNNING", "PLANNING", "PROCESSING", "NEW"].includes(value)) els.status.classList.add("running");
  if (value === "FINISHED") els.status.classList.add("finished");
  if (value === "FAILED") els.status.classList.add("failed");
  if (value === "CANCELLED") els.status.classList.add("cancelled");
}

async function request(path, options = {}) {
  const response = await fetch(`${state.apiBase}${path}`, { method: options.method || "GET", headers: { "Content-Type": "application/json", Authorization: state.userId }, body: options.body ? JSON.stringify(options.body) : undefined });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.message || body?.error || `Erro HTTP ${response.status}`);
  return body;
}

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
  const text = document.createElement("div");
  text.className = "markdown-text";
  if (typeof window.marked?.parse === "function" && typeof window.DOMPurify?.sanitize === "function") {
    text.innerHTML = window.DOMPurify.sanitize(window.marked.parse(value));
  } else {
    text.innerHTML = escapeHtml(value).replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>").replace(/\n/g, "<br>");
  }
  target.append(text);
}

function appendVegaChart(target, source) {
  const chart = document.createElement("div");
  chart.className = "vega-chart";
  target.append(chart);
  try {
    const spec = JSON.parse(source.trim());
    if (typeof window.vegaEmbed !== "function") throw new Error("Biblioteca Vega-Lite indisponivel.");
    const height = Number.isFinite(spec.height) ? Math.min(spec.height, 460) : 380;
    const responsiveSpec = {
      ...spec,
      width: "container",
      height,
      autosize: { type: "fit", contains: "padding", resize: true }
    };
    const tooltip = typeof window.vegaTooltip?.Handler === "function"
      ? new window.vegaTooltip.Handler({ theme: "dark" }).call
      : true;
    window.vegaEmbed(chart, responsiveSpec, { actions: false, renderer: "svg", tooltip })
      .catch((error) => renderChartError(chart, error.message));
  } catch (error) {
    renderChartError(chart, error.message);
  }
}

function renderChartError(chart, message) {
  chart.classList.add("failed");
  chart.textContent = `Nao foi possivel exibir este grafico: ${message}`;
}
function roleLabel(role) { return role === "user" ? "Voce" : role === "tool" ? "Tool" : "Hub de IA"; }
function formatDate(value) { if (!value) return ""; const date = new Date(value); return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date); }
function shortId(value) { return !value ? "Sem execucao" : value.length > 12 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value; }
function trimSlash(value) { return value.replace(/\/+$/, ""); }
function escapeHtml(value) { return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;"); }
function showToast(message) { const toast = document.createElement("div"); toast.className = "toast"; toast.textContent = message; document.body.appendChild(toast); window.setTimeout(() => toast.remove(), 4200); }

init();
