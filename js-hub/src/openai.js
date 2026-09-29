const { openAIAuth } = require('./auth');

async function callOpenAI(payload) {
    const timeoutMs = positiveInteger(process.env.OPENAI_TIMEOUT_MS, 60000);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch('https://api.openai.com/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${openAIAuth()}` },
            body: JSON.stringify(payload),
            signal: controller.signal
        });
        const body = await response.json();
        if (!response.ok) {
            const error = new Error(`OpenAI HTTP ${response.status}: ${body.error?.message || 'Request failed'}`);
            error.status = response.status;
            throw error;
        }
        return body;
    } catch (error) {
        if (error.name === 'AbortError') throw new Error(`OpenAI timeout apos ${timeoutMs}ms.`);
        throw error;
    } finally {
        clearTimeout(timeout);
    }
}

function positiveInteger(value, fallback) {
    const number = Number(value ?? fallback);
    if (!Number.isSafeInteger(number) || number < 1) throw new Error(`Limite invalido: ${value}`);
    return number;
}

function structuredOutput(name, schema) {
    return { type: 'json_schema', json_schema: { name, strict: true, schema } };
}

// Every assistant tool call needs a matching result before the next request.
async function runAgent({
    prompt, messages = [], tools = {}, model = process.env.OPENAI_MODEL || 'gpt-5',
    responseFormat, maxToolRounds = positiveInteger(process.env.OPENAI_MAX_TOOL_ROUNDS, 8),
    onRequest = async () => {}, onMessages = async () => {}, complete = callOpenAI
}) {
    positiveInteger(maxToolRounds, 8);
    const history = [{ role: 'system', content: prompt }, ...structuredClone(messages)];
    const schemas = Object.values(tools).map(tool => tool.schema);
    for (let round = 0; round <= maxToolRounds; round++) {
        const payload = { model, messages: history };
        if (schemas.length) {
            payload.tools = schemas;
            // Calls within one agent can share a mutable provider session.
            payload.parallel_tool_calls = false;
        }
        if (responseFormat) payload.response_format = responseFormat;
        await onRequest('start');
        const response = await complete(payload);
        await onRequest('done');
        const choice = response.choices?.[0];
        const message = choice?.message;
        if (!message || message.refusal) throw new Error(message?.refusal || 'OpenAI nao retornou mensagem.');
        if (choice.finish_reason === 'stop' && !message.tool_calls?.length) {
            if (!message.content?.trim()) throw new Error('Agente nao retornou conteudo.');
            history.push(message);
            await onMessages(history.slice(1));
            return message.content;
        }
        if (choice.finish_reason !== 'tool_calls' || !message.tool_calls?.length) {
            throw new Error(`Resposta incompleta do agente: ${choice.finish_reason}`);
        }
        if (round === maxToolRounds) throw new Error(`Limite de ${maxToolRounds} rodadas de tools atingido.`);
        const calls = message.tool_calls.map(call => {
            const tool = Object.hasOwn(tools, call.function.name) && tools[call.function.name];
            if (!tool) throw new Error(`Tool nao permitida para este agente: ${call.function.name}`);
            const args = JSON.parse(call.function.arguments);
            if (!args || typeof args !== 'object' || Array.isArray(args)) throw new Error('Argumentos de tool invalidos.');
            return { call, tool, args };
        });
        history.push(message);
        for (const { call, tool, args } of calls) {
            const result = await tool.execute(args);
            history.push({
                role: 'tool', tool_call_id: call.id,
                content: typeof result === 'string' ? result : JSON.stringify(result ?? null)
            });
        }
        await onMessages(history.slice(1));
    }
}

module.exports = { runAgent, callOpenAI, structuredOutput, positiveInteger };
