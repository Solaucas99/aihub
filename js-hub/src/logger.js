// Concentra todo console.log/console.error de uma execucao (planner+orchestrator+validator+communicator+tools+auth) em um unico log.
const { AsyncLocalStorage } = require('node:async_hooks');
const fs = require('fs');
const path = require('path');

const LOGS_DIR = path.join(__dirname, 'logs');
const als = new AsyncLocalStorage();

const originalLog = console.log.bind(console);
const originalError = console.error.bind(console);

function formatArgs(args) {
    return args.map(arg => typeof arg === 'string' ? arg : JSON.stringify(arg)).join(' ');
}

function writeToActiveStream(line) {
    const stream = als.getStore();
    if (stream) {
        stream.write(`[${new Date().toISOString()}] ${line}\n`);
    }
}

console.log = (...args) => {
    originalLog(...args);
    writeToActiveStream(formatArgs(args));
};

console.error = (...args) => {
    originalError(...args);
    writeToActiveStream(formatArgs(args));
};

function slugify(text) {
    return String(text)
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)/g, '')
        .slice(0, 60) || 'sem-mensagem';
}

async function runWithExecutionLog(message, fn) {
    fs.mkdirSync(LOGS_DIR, { recursive: true });
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filePath = path.join(LOGS_DIR, `${timestamp}_${slugify(message)}.log`);
    const stream = fs.createWriteStream(filePath, { flags: 'a' });
    stream.write(`=== Execução iniciada em ${new Date().toISOString()} ===\nMensagem: ${message}\n\n`);

    try {
        return await als.run(stream, fn);
    } finally {
        stream.write(`\n=== Execução finalizada em ${new Date().toISOString()} ===\n`);
        stream.end();
    }
}

module.exports = { runWithExecutionLog };
