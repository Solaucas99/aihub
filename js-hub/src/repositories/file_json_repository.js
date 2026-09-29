const fs = require('fs/promises');
const path = require('path');
const { randomUUID } = require('crypto');

class FileJsonRepository {
    static writeQueues = new Map();

    constructor(filePath, initialData = {}) {
        this.filePath = path.resolve(filePath);
        this.initialData = initialData;
    }

    async readAll() {
        try {
            return JSON.parse(await fs.readFile(this.filePath, 'utf8'));
        } catch (error) {
            if (error.code === 'ENOENT') return structuredClone(this.initialData);
            throw error;
        }
    }

    async writeAll(data) {
        await fs.mkdir(path.dirname(this.filePath), { recursive: true });
        const temporaryPath = `${this.filePath}.${randomUUID()}.tmp`;
        try {
            await fs.writeFile(temporaryPath, JSON.stringify(data, null, 2));
            await fs.rename(temporaryPath, this.filePath);
        } finally {
            await fs.unlink(temporaryPath).catch(error => {
                if (error.code !== 'ENOENT') throw error;
            });
        }
    }

    async updateAll(mutator) {
        // O store local e arquivo JSON; serialize updates para evitar perda de escrita em tasks paralelas.
        const previousQueue = FileJsonRepository.writeQueues.get(this.filePath) || Promise.resolve();
        const nextQueue = previousQueue.then(async () => {
            const data = await this.readAll();
            const nextData = await mutator(data);
            await this.writeAll(nextData || data);
            return nextData || data;
        });

        // Uma falha nao deve bloquear as proximas escritas. Libere filas ociosas.
        const settledQueue = nextQueue.catch(() => {}).finally(() => {
            if (FileJsonRepository.writeQueues.get(this.filePath) === settledQueue) {
                FileJsonRepository.writeQueues.delete(this.filePath);
            }
        });
        FileJsonRepository.writeQueues.set(this.filePath, settledQueue);
        return nextQueue;
    }

}

module.exports = { FileJsonRepository };
