const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { isDeepStrictEqual } = require('node:util');
const { FileWorkflowStore } = require('./file_workflow_store');
const { FileJsonRepository } = require('./file_json_repository');

async function migrateWorkflows({
    source = path.join(__dirname, '..', '..', 'data', 'workflows.json'),
    store = new FileWorkflowStore()
} = {}) {
    const marker = new FileJsonRepository(path.join(store.directory, 'migration.json'), null);
    const completed = await marker.readAll();

    let raw;
    try {
        raw = await fs.readFile(source, 'utf8');
    } catch (error) {
        if (error.code === 'ENOENT') return { migrated: 0 };
        throw error;
    }
    const sourceHash = createHash('sha256').update(raw).digest('hex');
    if (completed) {
        if (completed.source_sha256 !== sourceHash) {
            throw new Error('O arquivo legado mudou apos a migracao. Reconcilie os dados antes de iniciar o Hub.');
        }
        return { migrated: 0, alreadyComplete: true };
    }
    const legacy = JSON.parse(raw);
    if (!legacy.workflows || typeof legacy.workflows !== 'object' || Array.isArray(legacy.workflows)) {
        throw new Error('Arquivo legado de workflows invalido.');
    }

    let migrated = 0;
    for (const [id, workflow] of Object.entries(legacy.workflows)) {
        if (id !== workflow.workflow_id) throw new Error('ID inconsistente no arquivo legado.');
        await store.save(workflow, { onlyIfMissing: true });
        const restored = await store.getById(id);
        if (!isDeepStrictEqual(workflow, restored)) {
            throw new Error(`Workflow ${id} difere do legado. A migracao nao sobrescreveu os dados existentes.`);
        }
        migrated++;
    }
    if (await fs.readFile(source, 'utf8') !== raw) {
        throw new Error('O arquivo legado mudou durante a migracao. Pare o servidor antigo antes de migrar.');
    }
    await marker.writeAll({
        schema_version: 1,
        migrated_at: new Date().toISOString(),
        source_file: path.basename(source),
        source_sha256: sourceHash,
        workflow_count: migrated
    });
    return { migrated };
}

if (require.main === module) {
    migrateWorkflows().then(result => console.log('Migracao de workflows:', result)).catch(error => {
        console.error(error.message);
        process.exitCode = 1;
    });
}

module.exports = { migrateWorkflows };
