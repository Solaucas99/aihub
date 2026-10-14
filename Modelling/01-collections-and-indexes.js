// Hub de IA - 01: cria as 13 colecoes e todos os indices do modelo de dados (MongoDB).
//
// Executar (escolha uma forma):
//   mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 01-collections-and-indexes.js
//   docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin --quiet < 01-collections-and-indexes.js
//
// Idempotente: pode rodar mais de uma vez. Os NOMES dos indices precisam ficar exatamente como abaixo:
// a aplicacao (spring.data.mongodb.auto-index-creation=true) recria os mesmos indices na subida e,
// se ja existir um indice com as mesmas chaves e outro nome, o Mongo recusa (IndexOptionsConflict).

const DATABASE = 'appdb';
const database = db.getSiblingDB(DATABASE);

const collections = [
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
];

const existing = database.getCollectionNames();
for (const name of collections) {
  if (!existing.includes(name)) {
    database.createCollection(name);
    print('colecao criada: ' + name);
  } else {
    print('colecao ja existia: ' + name);
  }
}

const indexes = [
  { collection: "conversations", keys: {"user_id": 1, "updated_at": -1}, options: {} },
  { collection: "messages", keys: {"conversation_id": 1, "created_at": 1}, options: {} },
  { collection: "conversation_work_contexts", keys: {"conversation_id": 1, "work_context_id": 1}, options: {"unique": true} },
  { collection: "conversation_work_contexts", keys: {"conversation_id": 1, "status": 1}, options: {} },
  { collection: "agent_runs", keys: {"conversation_id": 1, "created_at": 1}, options: {} },
  { collection: "task_executions", keys: {"conversation_id": 1, "task_id": 1}, options: {} },
  { collection: "task_executions", keys: {"conversation_id": 1, "subagent_id": 1, "started_at": -1}, options: {} },
  { collection: "human_input_requests", keys: {"conversation_id": 1, "status": 1}, options: {} },
  { collection: "provider_sessions", keys: {"conversation_id": 1, "subagent_id": 1, "provider_id": 1}, options: {"unique": true} },
  { collection: "artifacts", keys: {"conversation_id": 1, "type": 1, "version": 1}, options: {"unique": true} },
  { collection: "artifacts", keys: {"conversation_id": 1, "type": 1, "status": 1}, options: {} },
  { collection: "artifacts", keys: {"conversation_id": 1, "created_at": -1}, options: {} },
  { collection: "skills", keys: {"key": 1}, options: {"unique": true, "name": "key"} },
  { collection: "work_contexts", keys: {"key": 1}, options: {"unique": true, "name": "key"} },
  { collection: "subagents", keys: {"name": 1}, options: {"name": "name"} },
  { collection: "subagents", keys: {"enabled": 1}, options: {"name": "enabled"} },
];

for (const index of indexes) {
  const name = database.getCollection(index.collection).createIndex(index.keys, index.options);
  print('indice ok: ' + index.collection + ' -> ' + name + (index.options.unique ? ' (unique)' : ''));
}

print('');
print('Pronto: ' + collections.length + ' colecoes e ' + indexes.length + ' indices em ' + DATABASE + '.');
