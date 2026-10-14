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
