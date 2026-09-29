const express = require('express');
const { createHub } = require('./create_hub');
const { WorkflowNotFoundError } = require('./repositories/workflow_not_found_error');
const { createChatSseHandler } = require('./controllers/chat_sse_controller');
const { buildWorkflowResultPayload } = require('./services/workflow_result_service');

function createApp({ hub, ...options } = {}) {
    const { workflowRepository, processChatTurn } = hub || createHub(options);
    const app = express();
    const handleChat = createChatSseHandler({ processChatTurn });
    app.use(allowCors);
    app.use(express.json());

    app.route('/')
        .get((req, res) => handleChat(parseChatParams(req.query), res))
        .post((req, res) => handleChat(parseChatParams(req.body), res));

    app.get('/workflows/:workflowId', async (req, res) => {
        res.json(await workflowRepository.requireById(req.params.workflowId));
    });
    app.get('/workflows/:workflowId/result', async (req, res) => {
        const workflow = await workflowRepository.requireById(req.params.workflowId);
        const result = buildWorkflowResultPayload(workflow);
        res.status(result.ready ? 200 : 202).json(result);
    });
    app.get('/conversations/:parentConversationId', async (req, res) => {
        res.json(await workflowRepository.getParentConversationView(req.params.parentConversationId));
    });
    app.get('/conversations/:parentConversationId/workflows', async (req, res) => {
        const parentConversationId = req.params.parentConversationId;
        const workflows = await workflowRepository.getByParentConversationId(parentConversationId);
        res.json({ parent_conversation_id: parentConversationId, workflows });
    });
    app.use(handleHttpError);
    return app;
}

function allowCors(req, res, next) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
}

function parseChatParams(input = {}) {
    return {
        message: input.message,
        workflowId: input.workflow_id,
        parentConversationId: input.parent_conversation_id,
        journeyHint: input.journey_hint,
        replyMode: input.reply_mode,
        includePayload: input.include_payload === true
            || ['1', 'true', 'yes', 'sim'].includes(String(input.include_payload || '').toLowerCase())
    };
}

function handleHttpError(error, req, res, next) {
    if (res.headersSent) return next(error);
    if (error instanceof WorkflowNotFoundError) return res.status(404).json({ message: error.message });
    if (error.type === 'entity.parse.failed') {
        return res.status(400).json({ message: 'O body deve conter JSON valido.' });
    }
    if (error.type === 'entity.too.large') {
        return res.status(413).json({ message: 'O body excede o tamanho permitido.' });
    }
    console.error('[HTTP] Falha ao processar solicitacao:', error.message);
    res.status(500).json({ message: 'Nao foi possivel processar esta solicitacao agora.' });
}

module.exports = { createApp };
