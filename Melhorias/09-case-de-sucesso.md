# 9. Como transformar o Hub em um case de sucesso

Os outros documentos listam **o que construir**. Este define **o que provar**, **como medir** e **como contar**. Um case é convincente quando tem: um problema real, uma solução que funciona sob olhar crítico e números antes/depois.

## 1. A narrativa

1. **Problema:** analisar campanhas e clientes exige pedir a um analista, esperar e não saber se o número está certo.
2. **Solução:** um assistente que **delega a especialistas que consultam a fonte real**, **confere** cada resultado e **lembra** o que já foi analisado.
3. **Prova:** tempo até o insight, qualidade medida, custo por pergunta e **tempo para adicionar um novo agente** (o diferencial de extensibilidade).

## 2. O que o case precisa demonstrar (as quatro promessas)

| Promessa | Evidência que sustenta | Melhorias necessárias |
|---|---|---|
| **Confiável** | Taxa de aprovação na 1ª tentativa, taxa de `FAILED`, resposta com fonte e consulta visíveis | OBS-1/3, QLD-1/2/7, CNF-1/2, PRD-1 |
| **Rápido** | Tempo até a primeira informação e tempo total (p50/p95) vs. o processo manual | OBS-4/6, CST-3/6, CNF-6 |
| **Econômico e previsível** | Custo por pergunta com orçamento por execução | OBS-1, CST-1/2 |
| **Extensível** | "Agente novo em produção em horas, sem código" | EXT-8, EXT-1, EXT-3 |

## 3. KPIs e metas sugeridas para o piloto

Definir a linha de base **antes** do piloto (como é hoje, com analistas) para que os ganhos sejam números e não impressões.

| KPI | Como medir | Meta sugerida |
|---|---|---|
| Tempo até o insight | Hub (do envio à resposta) × baseline manual | redução ≥ 70 % |
| Aprovação na 1ª tentativa | métrica `hub.validator.verdict` | > 80 % |
| Execuções `FAILED` | métrica `hub.run.duration{status}` | < 1 % |
| Satisfação | 👍 ÷ avaliações ([PRD-2](07-produto-e-experiencia.md)) | > 85 % |
| Respostas com fonte registrada | auditoria de amostra | 100 % das que têm números |
| Custo por pergunta que delega | [OBS-1](01-observabilidade.md) | dentro do orçamento acordado |
| Tempo para adicionar um especialista | cronometrar o roteiro de [EXT-8](02-extensibilidade.md) | < 1 dia, sem código |
| Adoção | usuários ativos por semana, perguntas por usuário | crescimento semana a semana |

As metas são ponto de partida para negociar, não fatos: ajustar com os dados das duas primeiras semanas.

## 4. Plano de piloto (4 a 6 semanas)

| Semana | Foco |
|---|---|
| 0 | Fundação: itens P0 ([README](README.md)); painéis no ar; baseline dos KPIs; conjunto dourado inicial (≥ 50 perguntas reais) |
| 1–2 | Grupo pequeno de usuários (5–10) em ambiente controlado; revisão diária das respostas ruins → novos casos de avaliação |
| 3–4 | Ajuste de prompts e catálogo com o gate de regressão; segundo especialista em produção (prova de extensibilidade) |
| 5–6 | Ampliar usuários; medir KPIs; relatório final e decisão de continuidade |

**Critérios de saída (go/no-go):** KPIs de confiabilidade e satisfação atingidos por duas semanas seguidas, nenhum incidente de dados, custo dentro do orçamento, e pelo menos um agente adicionado por pessoa não autora do código.

## 5. Roteiro de demonstração (10 minutos)

1. **Pergunta de dados** → mostrar as etapas em tempo real e a resposta com tabela e gráfico.
2. **"Agora em gráfico por canal"** → o Hub devolve ao mesmo especialista (continuidade, sem o planejador inventar).
3. **Pergunta ambígua** → pergunta de esclarecimento / HITL com botões; resposta com um toque.
4. **"Como cheguei nisso"** → fonte, consulta, data e vereditos do validador.
5. **Falha controlada** → derrubar o provedor de modelo; mostrar retry, disjuntor e mensagem clara (sem erro cru).
6. **Extensibilidade ao vivo** → criar o agente de **Briefing** na tela de catálogo e usá-lo na mesma conversa, sem reiniciar.
7. **Painel de observabilidade** → custo por pergunta, latência, taxa de aprovação.

Os passos 5 a 7 dependem de CNF-2, EXT-1/8, OBS-1/6; sem eles, a demonstração mostra apenas o caminho feliz.

## 6. Riscos e como tratá-los

| Risco | Efeito | Mitigação |
|---|---|---|
| Número errado apresentado com confiança | Perda de confiança | QLD-7 (fonte obrigatória), QLD-2 (validador calibrado), PRD-1 |
| Custo imprevisível | Projeto inviabilizado | OBS-1, CST-1/2/3, SEG-8 |
| Exposição sem autenticação real | Incidente de segurança | SEG-1/3/4 antes de qualquer acesso externo |
| Execução presa ou duplicada | Experiência ruim | CNF-1/5/7 |
| Mudança silenciosa do modelo do provedor | Regressão de qualidade | QLD-3 (avaliação noturna), OBS-8 |
| Dependência de um único provedor de dados/modelo | Risco de negócio | EXT-3/5, fallback |
| Documentação divergir do código | Perda de reprodutibilidade | ENG-3 (gate no CI) |

## 7. Como contar a história

- **Para executivos:** problema, três números (tempo, custo por pergunta, satisfação), uma demonstração curta e o plano de expansão. A apresentação em [`Documentacao/Hub-de-IA.pptx`](../Documentacao/Hub-de-IA.pptx) já cobre o "como funciona".
- **Para times técnicos:** arquitetura, garantias, limites honestos (a seção "limites" já é um ponto forte de credibilidade) e o roteiro de extensibilidade.
- **Para o futuro:** o repositório reproduzível (`specs/`, `Modelling/`, `Mermaid/`) como diferencial: qualquer equipe replica o projeto e **prova mecanicamente** que ficou igual.

Volte ao [índice e roadmap](README.md).
