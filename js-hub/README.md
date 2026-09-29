# Hub de IA

API Node.js para orquestrar jornadas, agentes e ferramentas, com chat via SSE.

## Executar

Use Node.js 24 (versao usada na verificacao local), instale as dependencias com
`npm install` e configure as credenciais no `.env` local.

```sh
npm start
```

Abra `frontend-chat/index.html` no navegador. A API usa a porta 3000 por padrao.
No PowerShell com scripts bloqueados, use `npm.cmd` nos comandos acima e abaixo.

## Persistencia

Cada workflow fica em `data/workflows/<workflow_id>/`, com documentos separados
para estado, tarefas, mensagens, eventos, sessoes e resultados. O arquivo
`data/workflows.json` e preservado como copia legada; novas escritas usam as pastas.
A primeira inicializacao migra os registros legados antes de abrir a porta HTTP.
Tambem e possivel executar a migracao explicitamente:

```sh
npm run migrate:workflows
npm run check
```

`check` verifica sintaxe; nao executa testes unitarios nem chama provedores externos.

## Onde alterar

- [Arquitetura](docs/architecture.md): responsabilidades e extensoes.
- [Persistencia](docs/workflow-persistence.md): formato, migracao e caminho para MongoDB.
- [Avaliacao e melhorias](docs/code-quality-review.md): diagnostico e alteracoes aplicadas.
- [Novas jornadas](docs/new-journey-guide.md): regras de negocio e registro.
- [Refatoracao de extensibilidade](docs/journey-extensibility-refactor.md): montagem do Hub e verificacao de equivalencia.
- [Frontend](frontend-chat/README.md): uso e renderizacao.
