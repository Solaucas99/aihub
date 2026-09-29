const fs = require('node:fs/promises');
const path = require('node:path');
const { isDeepStrictEqual } = require('node:util');
const { FileJsonRepository } = require('./file_json_repository');
const { WorkflowNotFoundError } = require('./workflow_not_found_error');
const { WORKFLOW_SECTIONS, splitWorkflow, joinWorkflow } = require('./workflow_documents');

const DEFAULT_DIRECTORY = path.join(__dirname, '..', '..', 'data', 'workflows');
const DOCUMENT_NAMES = new Set(['workflow', 'task_contexts', 'artifacts', ...Object.keys(WORKFLOW_SECTIONS)]);

class FileWorkflowStore {
    static queues = new Map();

    constructor(directory = DEFAULT_DIRECTORY) {
        this.directory = path.resolve(directory);
    }

    workflowDirectory(workflowId) {
        if (typeof workflowId !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(workflowId)) {
            throw new WorkflowNotFoundError(workflowId);
        }
        return path.join(this.directory, workflowId);
    }

    document(workflowId, name) {
        if (!DOCUMENT_NAMES.has(name) && name !== 'pending') throw new Error('Documento de workflow invalido.');
        return new FileJsonRepository(path.join(this.workflowDirectory(workflowId), `${name}.json`), null);
    }

    // Leituras e escritas do mesmo workflow compartilham a fila. Workflows distintos
    // nao disputam uma fila global. Este lock e local ao processo.
    async withWorkflow(workflowId, operation) {
        const key = this.workflowDirectory(workflowId);
        const previous = FileWorkflowStore.queues.get(key) || Promise.resolve();
        const current = previous.then(async () => {
            await this.recover(workflowId);
            return operation();
        });
        const settled = current.catch(() => {}).finally(() => {
            if (FileWorkflowStore.queues.get(key) === settled) FileWorkflowStore.queues.delete(key);
        });
        FileWorkflowStore.queues.set(key, settled);
        return current;
    }

    async getById(workflowId, { sections = null } = {}) {
        return this.withWorkflow(workflowId, async () => {
            const documents = await this.readDocuments(workflowId, sections);
            return documents ? joinWorkflow(documents) : null;
        });
    }

    async listByParent(parentConversationId, { excludeWorkflowId = null, sections = null } = {}) {
        let entries;
        try {
            entries = await fs.readdir(this.directory, { withFileTypes: true });
        } catch (error) {
            if (error.code === 'ENOENT') return [];
            throw error;
        }
        const workflows = [];
        for (const entry of entries) {
            if (!entry.isDirectory() || entry.name === excludeWorkflowId) continue;
            const workflow = await this.withWorkflow(entry.name, async () => {
                const header = await this.document(entry.name, 'workflow').readAll();
                if (header?.data.parent_conversation_id !== parentConversationId) return null;
                return joinWorkflow(await this.readDocuments(entry.name, sections));
            });
            if (workflow) workflows.push(workflow);
        }
        return workflows.sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
    }

    async save(workflow, { onlyIfMissing = false } = {}) {
        return this.withWorkflow(workflow.workflow_id, async () => {
            const existing = await this.readDocuments(workflow.workflow_id);
            if (onlyIfMissing && existing) return joinWorkflow(existing);
            await this.commit(workflow.workflow_id, existing || {}, splitWorkflow(workflow));
            return workflow;
        });
    }

    async updateById(workflowId, mutate) {
        return this.withWorkflow(workflowId, async () => {
            const previous = await this.readDocuments(workflowId);
            if (!previous) throw new WorkflowNotFoundError(workflowId);
            // A copia impede que mutacoes alterem os documentos usados na comparacao.
            const workflow = structuredClone(joinWorkflow(previous));
            await mutate(workflow);
            if (workflow.workflow_id !== workflowId) throw new Error('A identidade do workflow nao pode ser alterada.');
            await this.commit(workflowId, previous, splitWorkflow(workflow));
            return workflow;
        });
    }

    async readDocuments(workflowId, sections = null) {
        const header = await this.document(workflowId, 'workflow').readAll();
        if (!header) return null;
        if (header.schema_version !== 1 || header.workflow_id !== workflowId || !Array.isArray(header.sections)) {
            throw new Error('Metadados de workflow invalidos ou versao nao suportada.');
        }
        const names = header.sections.filter(name => !sections || sections.includes(name));
        const documents = { workflow: header };
        for (const name of names) {
            const document = await this.document(workflowId, name).readAll();
            if (!document || document.workflow_id !== workflowId || document.schema_version !== 1) {
                throw new Error(`Documento de workflow ausente ou invalido: ${name}.`);
            }
            documents[name] = document;
        }
        return documents;
    }

    async commit(workflowId, previous, next) {
        const changes = Object.fromEntries(Object.entries(next)
            .filter(([name, value]) => !isDeepStrictEqual(previous[name], value)));
        for (const name of Object.keys(previous)) {
            if (!Object.hasOwn(next, name)) changes[name] = null;
        }
        if (!Object.keys(changes).length) return;
        // Journal antes das escritas: uma interrupcao permite completar a mesma
        // transacao na proxima leitura, sem misturar status e mensagem de turnos.
        await this.document(workflowId, 'pending').writeAll(changes);
        await this.recover(workflowId);
    }

    async recover(workflowId) {
        const journal = this.document(workflowId, 'pending');
        const changes = await journal.readAll();
        if (!changes) return;
        for (const [name, document] of Object.entries(changes)) {
            if (!DOCUMENT_NAMES.has(name)) throw new Error('Journal de workflow invalido.');
            const target = this.document(workflowId, name);
            if (document === null) {
                await fs.unlink(target.filePath).catch(error => {
                    if (error.code !== 'ENOENT') throw error;
                });
            } else {
                await target.writeAll(document);
            }
        }
        await fs.unlink(journal.filePath);
    }
}

module.exports = { FileWorkflowStore };
