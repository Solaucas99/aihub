// Hub de IA - 02: dados iniciais do catalogo (providers, tools, skills, subagents, work_contexts).
//
// Executar DEPOIS do 01:
//   mongosh "mongodb://admin:admin123@localhost:27017/appdb?authSource=admin" --file 02-seed-catalog.js
//   docker exec -i mongodb mongosh -u admin -p admin123 --authenticationDatabase admin --quiet < 02-seed-catalog.js
//
// Mesma regra do bootstrap da aplicacao: so semeia uma colecao que esteja VAZIA; nunca altera nem recria
// o que ja existe. Os documentos abaixo foram gerados a partir de src/main/resources/bootstrap/*.json.
// O campo `id` de objetos embutidos (subagents.providers[]) e gravado como `_id`, como o Spring Data faz.
// Os space_id dos Genie Spaces aceitam variavel de ambiente na aplicacao (GENIE_MENSURACAO_SPACE_ID,
// GENIE_MAPA_CLIENTES_SPACE_ID, GENIE_DEFAULT_SPACE_ID); troque o `fallback` abaixo pelos ids da sua conta.

const DATABASE = 'appdb';
const database = db.getSiblingDB(DATABASE);

const catalog = {
  "providers": [
    {
      "id": "provider-genie",
      "name": "Genie",
      "category": "GENIE",
      "external_agent_id": null,
      "tools": [],
      "config": {},
      "enabled": true
    }
  ],
  "tools": [
    {
      "id": "tool-enviar-mensagem-genie",
      "provider_id": "provider-genie",
      "name": "Enviar mensagem Genie",
      "type": "FUNCTION",
      "function_name": "tool-enviar-mensagem-genie",
      "description": "Consulta a Genie e continua uma conversa existente quando disponivel.",
      "input_schema": {},
      "output_schema": {},
      "config": {},
      "enabled": true
    }
  ],
  "skills": [
    {
      "id": "skill-campaign-measurement",
      "key": "CAMPAIGN_MEASUREMENT",
      "name": "Mensuracao de campanhas",
      "description": "Consulta metricas, KPIs e desempenho de campanhas.",
      "instructions": "Use esta skill para analisar metricas, KPIs, conversoes e desempenho de campanhas. Consulte a fonte Genie quando precisar de dados.",
      "tool_ids": [
        "tool-enviar-mensagem-genie"
      ],
      "enabled": true
    },
    {
      "id": "skill-campaign-performance",
      "key": "CAMPAIGN_PERFORMANCE",
      "name": "Performance de campanhas",
      "description": "Interpreta resultados e comparativos de campanhas.",
      "instructions": "Use esta skill para interpretar resultados, tendencias e comparativos de campanhas. Consulte a fonte Genie quando precisar de dados.",
      "tool_ids": [
        "tool-enviar-mensagem-genie"
      ],
      "enabled": true
    },
    {
      "id": "skill-customer-profile",
      "key": "CUSTOMER_PROFILE",
      "name": "Perfil de clientes",
      "description": "Consulta perfis, renda, historico e caracteristicas de clientes.",
      "instructions": "Use esta skill para mapear perfis de clientes e seus historicos. Consulte a fonte Genie quando precisar de dados.",
      "tool_ids": [
        "tool-enviar-mensagem-genie"
      ],
      "enabled": true
    },
    {
      "id": "skill-customer-segmentation",
      "key": "CUSTOMER_SEGMENTATION",
      "name": "Segmentacao de clientes",
      "description": "Consulta segmentos, audiencias e propensao de compra.",
      "instructions": "Use esta skill para analisar segmentos, audiencias e propensao de compra. Consulte a fonte Genie quando precisar de dados.",
      "tool_ids": [
        "tool-enviar-mensagem-genie"
      ],
      "enabled": true
    }
  ],
  "subagents": [
    {
      "id": "agent-mensuracao",
      "name": "Agente Mensuracao",
      "description": "Responsavel por desempenho de campanhas, metricas, KPIs, taxas de conversao e resultados de marketing.",
      "system_prompt": "Voce e um agente especializado em mensuracao e analise de dados de desempenho de campanhas. Forneca respostas precisas e relevantes dentro do seu escopo. Sempre use a tool disponivel para consultar a fonte de dados quando precisar de informacoes factuais.",
      "skill_ids": [
        "skill-campaign-measurement",
        "skill-campaign-performance"
      ],
      "produces": [
        "INSIGHT"
      ],
      "consumes": [],
      "providers": [
        {
          "provider_id": "provider-genie",
          "config": {
            "space_id": {
              "$env": "GENIE_MENSURACAO_SPACE_ID",
              "fallback_env": "GENIE_DEFAULT_SPACE_ID",
              "fallback": "01f1a959ef7115c789ed26617f2d915e"
            }
          },
          "enabled": true,
          "_id": "subagent-provider-mensuracao-genie"
        }
      ],
      "enabled": true
    },
    {
      "id": "agent-mapa-clientes",
      "name": "Agente Mapa de Clientes",
      "description": "Responsavel por dados de clientes, perfil, segmentacao, renda, historico de interacoes e propensao de compra.",
      "system_prompt": "Voce e um agente especializado em mapear clientes e suas interacoes. Forneca respostas precisas e relevantes dentro do seu escopo. Sempre use a tool disponivel para consultar a fonte de dados quando precisar de informacoes factuais.",
      "skill_ids": [
        "skill-customer-profile",
        "skill-customer-segmentation"
      ],
      "providers": [
        {
          "provider_id": "provider-genie",
          "config": {
            "space_id": {
              "$env": "GENIE_MAPA_CLIENTES_SPACE_ID",
              "fallback_env": "GENIE_DEFAULT_SPACE_ID",
              "fallback": "01f1a959c5241402bb43591d3b51116c"
            }
          },
          "enabled": true,
          "_id": "subagent-provider-mapa-clientes-genie"
        }
      ],
      "enabled": true
    }
  ],
  "work_contexts": [
    {
      "id": "work-context-analise-dados",
      "key": "ANALISE_DE_DADOS",
      "name": "Analise de Dados",
      "description": "Analises de clientes, segmentacao, campanhas, metricas e desempenho.",
      "instructions": "Use dados verificaveis, explique limitacoes e delegue para os especialistas disponiveis quando a solicitacao exigir consulta de dados.",
      "agent_ids": [
        "agent-mensuracao",
        "agent-mapa-clientes"
      ],
      "enabled": true
    }
  ],
};

for (const [name, documents] of Object.entries(catalog)) {
  const collection = database.getCollection(name);
  if (collection.countDocuments() > 0) {
    print('ja populada, nada a fazer: ' + name);
    continue;
  }
  collection.insertMany(documents);
  print('semeada: ' + name + ' (' + documents.length + ' documento(s))');
}
