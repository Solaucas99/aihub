const journey = db.journeys.findOne({ hint: 'INSIGHTS' }) || {
  _id: 'journey-insights',
  hint: 'INSIGHTS',
  name: 'Insights',
  description: 'Jornada para analise e geracao de insights.'
};

db.journeys.updateOne({ _id: journey._id }, { $set: journey }, { upsert: true });

const agents = [
  {
    _id: 'agent-mensuracao',
    name: 'Agente Mensuracao',
    description: 'Agente responsavel pelos dados de desempenho de campanhas: metricas, KPIs, taxas de conversao e resultados de campanhas de marketing.',
    system_prompt: 'Voce e um agente especializado em mensuracao e analise de dados de desempenho de campanhas.\nSua funcao e fornecer informacoes detalhadas sobre metricas, KPIs e resultados de campanhas.\nQuando o usuario fizer perguntas relacionadas a esses topicos, voce deve fornecer respostas precisas e relevantes, utilizando dados e exemplos sempre que possivel.\nSe o usuario fizer perguntas fora do escopo da mensuracao, informe educadamente que nao pode ajudar com esse assunto especifico.\n\nSempre use a tool enviarMensagemGenie para consultar sua fonte de dados. Informe apenas a mensagem de negocio; o Hub gerencia provider, sessao e IDs tecnicos.',
    journey,
    capabilities: [
      'CAMPAIGN_MEASUREMENT',
      'CAMPAIGN_PERFORMANCE'
    ],
    providers: [
      {
        id: 'subagent-provider-mensuracao-genie',
        provider_id: 'provider-genie-mensuracao',
        tools_for_usage: [
          'tool-enviar-mensagem-genie'
        ],
        config: {
          space_id: {
            '$env': 'GENIE_MENSURACAO_SPACE_ID',
            fallback_env: 'GENIE_DEFAULT_SPACE_ID',
            fallback: '01f1a959ef7115c789ed26617f2d915e'
          }
        },
        enabled: true
      }
    ],
    enabled: true
  },
  {
    _id: 'agent-mapa-clientes',
    name: 'Agente Mapa de Clientes',
    description: 'Agente responsavel pelos dados de clientes: perfil, segmentacao, renda, historico de interacoes e propensao de compra.',
    system_prompt: 'Voce e um agente especializado em mapear clientes e suas interacoes.\nSua funcao e fornecer informacoes detalhadas sobre segmentacao de clientes e historico de interacoes.\nQuando o usuario fizer perguntas relacionadas a esses topicos, voce deve fornecer respostas precisas e relevantes, utilizando dados e exemplos sempre que possivel.\nSe o usuario fizer perguntas fora do escopo do mapeamento de clientes, informe educadamente que nao pode ajudar com esse assunto especifico.\n\nSempre use a tool enviarMensagemGenie para consultar sua fonte de dados. Informe apenas a mensagem de negocio; o Hub gerencia provider, sessao e IDs tecnicos.',
    journey,
    capabilities: [
      'CUSTOMER_PROFILE',
      'CUSTOMER_SEGMENTATION'
    ],
    providers: [
      {
        id: 'subagent-provider-mapa-clientes-genie',
        provider_id: 'provider-genie-mapa-clientes',
        tools_for_usage: [
          'tool-enviar-mensagem-genie'
        ],
        config: {
          space_id: {
            '$env': 'GENIE_MAPA_CLIENTES_SPACE_ID',
            fallback_env: 'GENIE_DEFAULT_SPACE_ID',
            fallback: '01f1a959c5241402bb43591d3b51116c'
          }
        },
        enabled: true
      }
    ],
    enabled: true
  }
];

for (const agent of agents) {
  db.subagents.updateOne({ _id: agent._id }, { $set: agent }, { upsert: true });
}

printjson(db.subagents.find(
  { _id: { $in: agents.map(agent => agent._id) } },
  {
    _id: 1,
    name: 1,
    capabilities: 1,
    providers: 1,
    enabled: 1,
    'journey.hint': 1
  }
).toArray());
