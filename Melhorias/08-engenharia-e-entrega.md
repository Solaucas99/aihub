# 8. Engenharia e entrega

**Tese:** um case é também um repositório que **qualquer pessoa consegue subir em dez minutos** e que **se protege sozinho** de regressões. Hoje o conhecimento está bem documentado (`specs/`, `Modelling/`, `Mermaid/`, `Documentacao/`), mas a automação que o mantém correto ainda não está no repositório.

## Estado atual (verificado no código)

| Tema | Situação |
|---|---|
| Empacotamento | Sem `Dockerfile`; só `docker-compose.yaml` com MongoDB |
| CI/CD | Nenhum (sem `.github/` nem equivalente) |
| Testes de integração | Dependem de um MongoDB em `localhost:27017` com `admin/admin123` (`MongoIntegrationTest`); sem ele, falham por conexão recusada (aconteceu ao reconstruir o projeto a partir das specs) |
| Geradores de documentação | Os scripts que **geram** `specs/`, `Modelling/` e `Mermaid/` (e os que **auditam** e **reconstroem** o projeto a partir delas) foram criados em uma pasta temporária **fora do repositório**. Só o `Mermaid/verify_project.py` está versionado |
| API | Rotas duplicadas por legado (`/conversation/{id}` e `/conversations/{id}`), sem especificação OpenAPI nem versão |
| Esquema do banco | Índices criados por `auto-index-creation`; scripts em `Modelling/`; sem controle de versão de esquema |
| Repositório | Itens soltos na raiz (`payload_genie.json`, `scripts/seed-subagents.js`, `js-hub/`), `HELP.md` |

---

### ENG-3 · Trazer os geradores e verificadores para o repositório
**P0 · S · Alto — risco imediato**

- **Por quê:** as specs são "geradas e verificadas mecanicamente"; se os geradores se perderem, elas passam a ser mantidas à mão e voltam a divergir do código.
- **Proposta:** pasta `tools/` com os geradores (specs, modelagem, diagramas), a auditoria e o teste de reconstrução; um comando único (`tools/regen.sh`) e um passo de CI que **falha se `specs/`, `Modelling/` ou `Mermaid/` estiverem desatualizados** em relação ao código.
- **Pronto quando:** mudar uma classe sem regenerar os artefatos reprova o pipeline.

### ENG-1 · Dockerfile e *compose* completo
**P0 · S · Alto**

`Dockerfile` multi-estágio (build com Maven, imagem final enxuta, usuário não-root, `HEALTHCHECK`) e um `docker-compose` que sobe **Hub + MongoDB + coletor OpenTelemetry + Prometheus + Grafana (+ Loki/Tempo)**. `docker compose up` entrega a aplicação e os painéis de [OBS-6](01-observabilidade.md) prontos para a demonstração.

### ENG-2 · CI com Testcontainers
**P0 · M · Alto**

Pipeline: compilar, testar, verificar artefatos de documentação ([ENG-3](#eng-3--trazer-os-geradores-e-verificadores-para-o-repositório)), construir a imagem. Trocar a dependência de um MongoDB local por **Testcontainers** em `MongoIntegrationTest`, para que os 72 testes rodem em qualquer máquina. Job noturno para os evals com modelo real ([QLD-3](03-qualidade-e-avaliacao-de-ia.md)), com chave em segredo do CI.

### ENG-4 · OpenAPI e versionamento da API
**P1 · S · Médio**

`springdoc-openapi` para publicar o contrato; prefixo `/v1`; **remover as rotas duplicadas** de legado (ou marcá-las como obsoletas); testes de contrato. Facilita novos clientes e canais ([EXT-9](02-extensibilidade.md)).

### ENG-5 · Regras arquiteturais e cobertura
**P1 · S · Médio**

ArchUnit codificando o desenho em camadas (`api → application → domain`, providers sem dependência de planejador), JaCoCo com piso de cobertura, Spotless/Checkstyle. Mantém o ganho de simplicidade da refatoração.

### ENG-6 · Controle de versão do esquema do MongoDB
**P1 · M · Médio**

Coleção `schema_migrations` e migrações versionadas (por exemplo Mongock) executadas na subida, em vez de depender de `auto-index-creation` e de scripts manuais. Os scripts de `Modelling/` servem de ponto de partida e deixam de ser o único caminho.

### ENG-7 · Limpeza do repositório
**P1 · S · Baixo**

Decidir o destino de `payload_genie.json`, `scripts/seed-subagents.js`, `js-hub/` e `HELP.md` (mover para `examples/`, documentar ou remover); manter um `.gitignore` coerente (`target/`, `logs/`, `.env`).

### ENG-8 · Perfis e configuração por ambiente
**P1 · S · Médio**

Perfis `dev`, `stage`, `prod`; `application.yaml` sem credenciais; *feature flags* simples (por exemplo bootstrap, SSE, validador determinístico); tabela única de todas as variáveis de ambiente no README.

---

## Definição de "pronto para ser exposto"

- [ ] `docker compose up` sobe tudo, com painéis.
- [ ] CI verde, com testes de integração em contêiner.
- [ ] Artefatos de documentação conferidos no CI.
- [ ] Sem segredo no repositório; perfis separados.
- [ ] OpenAPI publicada; rotas legadas removidas.

Próximo: [Case de sucesso](09-case-de-sucesso.md).
