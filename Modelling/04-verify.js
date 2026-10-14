// Hub de IA - 04: confere se o modelo de dados esta como esperado (somente leitura).
//   mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 04-verify.js

const DATABASE = 'appdb';
const database = db.getSiblingDB(DATABASE);

const expectedIndexes = {
  "conversations": [{"keys": {"user_id": 1, "updated_at": -1}, "unique": false}],
  "messages": [{"keys": {"conversation_id": 1, "created_at": 1}, "unique": false}],
  "conversation_work_contexts": [{"keys": {"conversation_id": 1, "work_context_id": 1}, "unique": true}, {"keys": {"conversation_id": 1, "status": 1}, "unique": false}],
  "agent_runs": [{"keys": {"conversation_id": 1, "created_at": 1}, "unique": false}],
  "task_executions": [{"keys": {"conversation_id": 1, "task_id": 1}, "unique": false}, {"keys": {"conversation_id": 1, "subagent_id": 1, "started_at": -1}, "unique": false}],
  "human_input_requests": [{"keys": {"conversation_id": 1, "status": 1}, "unique": false}],
  "provider_sessions": [{"keys": {"conversation_id": 1, "subagent_id": 1, "provider_id": 1}, "unique": true}],
  "artifacts": [{"keys": {"conversation_id": 1, "type": 1, "version": 1}, "unique": true}, {"keys": {"conversation_id": 1, "type": 1, "status": 1}, "unique": false}, {"keys": {"conversation_id": 1, "created_at": -1}, "unique": false}],
  "skills": [{"keys": {"key": 1}, "unique": true}],
  "work_contexts": [{"keys": {"key": 1}, "unique": true}],
  "subagents": [{"keys": {"name": 1}, "unique": false}, {"keys": {"enabled": 1}, "unique": false}],
};
const expectedCounts = { providers: 1, tools: 1, skills: 4, subagents: 2, work_contexts: 1 };

let problems = 0;
const names = database.getCollectionNames();
for (const name of [
  "providers",
  "tools",
  "skills",
  "subagents",
  "work_contexts",
  "conversations",
  "messages",
  "conversation_work_contexts",
  "agent_runs",
  "task_executions",
  "human_input_requests",
  "provider_sessions",
  "artifacts"
]) {
  if (!names.includes(name)) { print('FALTA colecao: ' + name); problems++; }
}
for (const [collection, wanted] of Object.entries(expectedIndexes)) {
  const actual = database.getCollection(collection).getIndexes();
  for (const want of wanted) {
    const found = actual.find((index) => JSON.stringify(index.key) === JSON.stringify(want.keys));
    if (!found) { print('FALTA indice: ' + collection + ' ' + JSON.stringify(want.keys)); problems++; }
    else if (Boolean(found.unique) !== want.unique) { print('UNIQUE divergente: ' + collection + ' ' + JSON.stringify(want.keys)); problems++; }
  }
}
for (const [collection, count] of Object.entries(expectedCounts)) {
  const actual = database.getCollection(collection).countDocuments();
  if (actual < count) { print('CATALOGO incompleto: ' + collection + ' tem ' + actual + ', esperado ao menos ' + count); problems++; }
}
print(problems === 0 ? 'OK: colecoes, indices e catalogo conferem.' : 'ATENCAO: ' + problems + ' problema(s) acima.');
