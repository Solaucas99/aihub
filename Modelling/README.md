# Modelling — modelo de dados do Hub de IA (MongoDB)

Esta pasta traz tudo o que é preciso para **criar manualmente** o banco do Hub de IA numa máquina nova, sem depender da aplicação: as coleções, os índices e os dados iniciais do catálogo.

| Arquivo | Para que serve |
|---|---|
| `01-collections-and-indexes.js` | Cria as **13 coleções** e os **16 índices** (inclusive os 5 únicos). Idempotente |
| `02-seed-catalog.js` | Insere o catálogo inicial: 1 provider, 1 tool, 4 skills, 2 subagentes e 1 work context. Só semeia coleção **vazia** |
| `03-data-model.md` | Referência completa: campos, tipos, enums, relações e regras de cada coleção |
| `04-verify.js` | Confere (somente leitura) se coleções, índices e catálogo estão corretos |

## Passo a passo

Pré-requisito: um MongoDB 8 acessível (o projeto usa `docker compose up -d`, com usuário `admin`, senha `admin123`, banco `appdb`).

**Com `mongosh` instalado:**

```text
mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 01-collections-and-indexes.js
mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 02-seed-catalog.js
mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 04-verify.js
```

**Sem `mongosh` local, usando o container (`mongodb`):**

```text
docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin --quiet < 01-collections-and-indexes.js
docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin --quiet < 02-seed-catalog.js
docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin --quiet < 04-verify.js
```

Esperado no final do `04-verify.js`: `OK: colecoes, indices e catalogo conferem.`

## Pontos de atenção

1. **Banco diferente?** Os scripts usam `appdb`. Para outro nome, altere a constante `DATABASE` no topo dos três `.js` e a variável `MONGODB_URI` da aplicação.
2. **Nomes dos índices importam.** A aplicação (`spring.data.mongodb.auto-index-creation=true`) recria os mesmos índices ao subir. Se você criar um índice com as mesmas chaves mas **outro nome**, o Mongo recusa com `IndexOptionsConflict`. Os scripts já usam os nomes certos (os índices de campo único do catálogo se chamam `key`, `name` e `enabled`; os compostos usam o nome padrão do Mongo, como `conversation_id_1_type_1_version_1`).
3. **Genie Spaces.** Em `02-seed-catalog.js`, cada subagente aponta para um Genie Space por `config.space_id = { "$env": ..., "fallback_env": ..., "fallback": "<id>" }`. Os `fallback` vieram do projeto original; **troque pelos ids da sua conta** (ou defina `GENIE_MENSURACAO_SPACE_ID`, `GENIE_MAPA_CLIENTES_SPACE_ID` ou `GENIE_DEFAULT_SPACE_ID` no ambiente da aplicação).
4. **Só o catálogo é semeado.** Conversas, mensagens, runs, execuções, artefatos, pendências e sessões são criados pela própria aplicação durante o uso; aqui só existem as coleções e os índices delas.
5. **Banco já em uso?** O `02` não sobrescreve nada: coleções com dados são ignoradas. Para recomeçar do zero (apaga **tudo**): `mongosh ".../appdb?authSource=admin" --eval "db.dropDatabase()"` e rode `01` e `02` de novo.
6. **A aplicação também semeia sozinha** o mesmo catálogo na primeira subida com o banco vazio (`hub.bootstrap.enabled=true`, o padrão). Rodar estes scripts antes da aplicação é equivalente e dá o mesmo resultado.
