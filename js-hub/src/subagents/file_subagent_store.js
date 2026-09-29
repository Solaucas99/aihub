const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, '..', '..', 'data');
const SUBAGENTS_FILE = path.join(DATA_DIR, 'subagents.json');

function loadFileSubAgents() {
    if (!fs.existsSync(SUBAGENTS_FILE)) {
        throw new Error(`Repositorio de subagentes nao encontrado em ${SUBAGENTS_FILE}.`);
    }

    const raw = fs.readFileSync(SUBAGENTS_FILE, 'utf8');
    const parsed = JSON.parse(raw);
    const subagents = Array.isArray(parsed) ? parsed : parsed.subagents || [];
    return resolveEnvValues(subagents);
}

function resolveEnvValues(value) {
    if (Array.isArray(value)) {
        return value.map(resolveEnvValues);
    }

    if (value && typeof value === 'object') {
        if (value.$env) {
            return process.env[value.$env]
                || process.env[value.fallback_env]
                || value.fallback
                || null;
        }

        return Object.fromEntries(
            Object.entries(value).map(([key, entryValue]) => [key, resolveEnvValues(entryValue)])
        );
    }

    return value;
}

module.exports = { loadFileSubAgents };
