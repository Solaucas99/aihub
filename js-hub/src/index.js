require('dotenv').config();
const { createApp } = require('./app');
const { migrateWorkflows } = require('./repositories/migrate_workflows');

const app = createApp();

async function startServer() {
    await migrateWorkflows();
    const port = process.env.PORT || 3000;
    const server = app.listen(port, () => console.log('Servidor rodando na porta ' + port));
    server.requestTimeout = 0;
    server.headersTimeout = 0;
    server.keepAliveTimeout = 0;
    server.setTimeout(0);
}

if (require.main === module) {
    startServer().catch(error => {
        console.error('Falha ao iniciar o Hub:', error.message);
        process.exitCode = 1;
    });
}

module.exports = { app };
