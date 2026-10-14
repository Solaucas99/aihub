# 04 — Catálogo declarativo (agentes, skills, tools, providers, contextos) e carga inicial

> Spec do projeto **Hub de IA** (pacote base `com.lamorim.hubdeia`). Leia antes `00-LEIA-ME.md` e `03-dominio-e-mongodb.md`.

## Objetivo

Definir o **catálogo declarativo** que descreve quem faz o quê no sistema — work contexts, subagentes, skills, tools e providers — como ele é semeado na primeira execução e como o backend aplica permissões a partir dele. O catálogo vive **no MongoDB**, que é a única fonte da verdade; os JSONs de `src/main/resources/bootstrap` são apenas uma carga inicial.

## Arquivos cobertos

- `src/main/resources/bootstrap/providers.json`, `tools.json`, `skills.json`, `subagents.json`, `work-contexts.json`
- `src/main/java/com/lamorim/hubdeia/config/BootstrapConfiguration.java`
- `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicy.java`
- Estrutura dos records `Provider`, `Skill`, `Subagent`, `SubagentProvider`, `Tool`, `WorkContext` (código literal em `03-dominio-e-mongodb.md`)

## Depende de

`02` (propriedade `hub.bootstrap.enabled`), `03` (records e repositórios do catálogo).

## 1. Modelo conceitual

```mermaid
flowchart LR
  WC["WorkContext<br/>(modo de trabalho)"] -->|agent_ids| SA["Subagent<br/>(especialista)"]
  SA -->|skill_ids| SK["Skill<br/>(capacidade + instruções)"]
  SK -->|tool_ids| TL["Tool<br/>(função exposta ao modelo)"]
  TL -->|provider_id| PV["Provider<br/>(fonte de dados)"]
  SA -->|providers[]| SP["SubagentProvider<br/>(habilita o provider + config própria)"]
  SA -.->|produces / consumes| AR["tipos de Artifact"]
```

| Entidade | Papel | Campos-chave |
|---|---|---|
| `WorkContext` | Agrupa especialistas **elegíveis** para um modo de trabalho; **não** limita a conversa do usuário | `id`, `key` (único), `name`, `description`, `instructions` (texto injetado nos prompts), `agent_ids`, `enabled` |
| `Subagent` | Especialista com prompt próprio | `system_prompt`, `skill_ids`, `providers[]`, `produces[]`, `consumes[]`, `enabled` |
| `Skill` | Capacidade de negócio: instruções + tools permitidas | `instructions`, `tool_ids`, `key` (único), `enabled` |
| `Tool` | Função que o modelo pode chamar | `provider_id`, `function_name` (nome exposto e chave do registro), `enabled` |
| `Provider` | Fonte de dados externa | `category` (ex.: `GENIE`), `enabled` |
| `SubagentProvider` | Liga um subagente a um provider **com configuração própria** (ex.: `space_id`) | `provider_id`, `config`, `enabled` |

Regras de negócio do catálogo:

- A tarefa do planejador **só escolhe subagentes** (nunca tools). As tools que um subagente pode usar são calculadas pelo backend (seção 4).
- `produces`/`consumes` são **tipos de artefato** (strings como `INSIGHT`, `BRIEFING`). Quando uma task de um subagente que `produces` é aprovada, o resultado vira artefato daquele tipo; o prompt de um subagente que `consumes` recebe o artefato ativo mais recente do tipo; e dentro de uma run quem consome espera quem produz (ver 07/08).
- Skills são, hoje, **trechos de prompt + permissão de tools**; várias skills podem apontar para a mesma tool.
- `Subagent.providers[].config.space_id` pode ser texto ou `{ "$env": "...", "fallback_env": "...", "fallback": "..." }` (resolução em `09-provider-genie.md`).

## 2. Dados semente (literais)

**Arquivo `src/main/resources/bootstrap/providers.json`** (literal):

```json
[
  {
    "id": "provider-genie",
    "name": "Genie",
    "category": "GENIE",
    "external_agent_id": null,
    "tools": [],
    "config": {},
    "enabled": true
  }
]
```

**Arquivo `src/main/resources/bootstrap/tools.json`** (literal):

```json
[
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
]
```

**Arquivo `src/main/resources/bootstrap/skills.json`** (literal):

```json
[
  {
    "id": "skill-campaign-measurement",
    "key": "CAMPAIGN_MEASUREMENT",
    "name": "Mensuracao de campanhas",
    "description": "Consulta metricas, KPIs e desempenho de campanhas.",
    "instructions": "Use esta skill para analisar metricas, KPIs, conversoes e desempenho de campanhas. Consulte a fonte Genie quando precisar de dados.",
    "tool_ids": ["tool-enviar-mensagem-genie"],
    "enabled": true
  },
  {
    "id": "skill-campaign-performance",
    "key": "CAMPAIGN_PERFORMANCE",
    "name": "Performance de campanhas",
    "description": "Interpreta resultados e comparativos de campanhas.",
    "instructions": "Use esta skill para interpretar resultados, tendencias e comparativos de campanhas. Consulte a fonte Genie quando precisar de dados.",
    "tool_ids": ["tool-enviar-mensagem-genie"],
    "enabled": true
  },
  {
    "id": "skill-customer-profile",
    "key": "CUSTOMER_PROFILE",
    "name": "Perfil de clientes",
    "description": "Consulta perfis, renda, historico e caracteristicas de clientes.",
    "instructions": "Use esta skill para mapear perfis de clientes e seus historicos. Consulte a fonte Genie quando precisar de dados.",
    "tool_ids": ["tool-enviar-mensagem-genie"],
    "enabled": true
  },
  {
    "id": "skill-customer-segmentation",
    "key": "CUSTOMER_SEGMENTATION",
    "name": "Segmentacao de clientes",
    "description": "Consulta segmentos, audiencias e propensao de compra.",
    "instructions": "Use esta skill para analisar segmentos, audiencias e propensao de compra. Consulte a fonte Genie quando precisar de dados.",
    "tool_ids": ["tool-enviar-mensagem-genie"],
    "enabled": true
  }
]
```

**Arquivo `src/main/resources/bootstrap/subagents.json`** (literal):

```json
[
  {
    "id": "agent-mensuracao",
    "name": "Agente Mensuracao",
    "description": "Responsavel por desempenho de campanhas, metricas, KPIs, taxas de conversao e resultados de marketing.",
    "system_prompt": "Voce e um agente especializado em mensuracao e analise de dados de desempenho de campanhas. Forneca respostas precisas e relevantes dentro do seu escopo. Sempre use a tool disponivel para consultar a fonte de dados quando precisar de informacoes factuais.",
    "skill_ids": ["skill-campaign-measurement", "skill-campaign-performance"],
    "produces": ["INSIGHT"],
    "consumes": [],
    "providers": [
      {
        "id": "subagent-provider-mensuracao-genie",
        "provider_id": "provider-genie",
        "config": {
          "space_id": {
            "$env": "GENIE_MENSURACAO_SPACE_ID",
            "fallback_env": "GENIE_DEFAULT_SPACE_ID",
            "fallback": "01f1a959ef7115c789ed26617f2d915e"
          }
        },
        "enabled": true
      }
    ],
    "enabled": true
  },
  {
    "id": "agent-mapa-clientes",
    "name": "Agente Mapa de Clientes",
    "description": "Responsavel por dados de clientes, perfil, segmentacao, renda, historico de interacoes e propensao de compra.",
    "system_prompt": "Voce e um agente especializado em mapear clientes e suas interacoes. Forneca respostas precisas e relevantes dentro do seu escopo. Sempre use a tool disponivel para consultar a fonte de dados quando precisar de informacoes factuais.",
    "skill_ids": ["skill-customer-profile", "skill-customer-segmentation"],
    "providers": [
      {
        "id": "subagent-provider-mapa-clientes-genie",
        "provider_id": "provider-genie",
        "config": {
          "space_id": {
            "$env": "GENIE_MAPA_CLIENTES_SPACE_ID",
            "fallback_env": "GENIE_DEFAULT_SPACE_ID",
            "fallback": "01f1a959c5241402bb43591d3b51116c"
          }
        },
        "enabled": true
      }
    ],
    "enabled": true
  }
]
```

**Arquivo `src/main/resources/bootstrap/work-contexts.json`** (literal):

```json
[
  {
    "id": "work-context-analise-dados",
    "key": "ANALISE_DE_DADOS",
    "name": "Analise de Dados",
    "description": "Analises de clientes, segmentacao, campanhas, metricas e desempenho.",
    "instructions": "Use dados verificaveis, explique limitacoes e delegue para os especialistas disponiveis quando a solicitacao exigir consulta de dados.",
    "agent_ids": ["agent-mensuracao", "agent-mapa-clientes"],
    "enabled": true
  }
]
```

Resumo do que a semente cria: 1 provider (`provider-genie`), 1 tool (`tool-enviar-mensagem-genie`), 4 skills, 2 subagentes (`agent-mensuracao`, que **produz** `INSIGHT`; e `agent-mapa-clientes`, que não produz nem consome) e 1 work context (`work-context-analise-dados`, chave `ANALISE_DE_DADOS`) com os dois subagentes. Os nomes das variáveis de ambiente de `space_id` são `GENIE_MENSURACAO_SPACE_ID` e `GENIE_MAPA_CLIENTES_SPACE_ID` (ambos com fallback para `GENIE_DEFAULT_SPACE_ID` e, por fim, o id literal do JSON).

## 3. `BootstrapConfiguration`

**Arquivo `src/main/java/com/lamorim/hubdeia/config/BootstrapConfiguration.java`** (literal):

```java
package com.lamorim.hubdeia.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.lamorim.hubdeia.domain.model.Provider;
import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.ProviderRepository;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

/**
 * First-run seed so a new environment has a working catalog. MongoDB is the source of truth: a
 * collection is seeded only while it is empty, and nothing here ever updates or recreates what
 * lives in the database afterwards. Set {@code hub.bootstrap.enabled=false} to skip it entirely.
 */
@Configuration
public class BootstrapConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(BootstrapConfiguration.class);

	private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	@Bean
	ApplicationRunner bootstrapCatalog(
			@Value("${hub.bootstrap.enabled:true}") boolean enabled,
			ProviderRepository providers,
			ToolRepository tools,
			SkillRepository skills,
			SubagentRepository subagents,
			WorkContextRepository workContexts
	) {
		return args -> {
			if (!enabled) {
				return;
			}
			seedIfEmpty(providers, "providers", new TypeReference<List<Provider>>() {});
			seedIfEmpty(tools, "tools", new TypeReference<List<Tool>>() {});
			seedIfEmpty(skills, "skills", new TypeReference<List<Skill>>() {});
			seedIfEmpty(subagents, "subagents", new TypeReference<List<Subagent>>() {});
			seedIfEmpty(workContexts, "work-contexts", new TypeReference<List<WorkContext>>() {});
		};
	}

	private <T> void seedIfEmpty(MongoRepository<T, String> repository, String file, TypeReference<List<T>> type) throws IOException {
		if (repository.count() > 0) {
			return;
		}
		try (var input = new ClassPathResource("bootstrap/" + file + ".json").getInputStream()) {
			List<T> packaged = mapper.readValue(input, type);
			repository.saveAll(packaged);
			logger.info("Seeded {} catalog entries from bootstrap/{}.json", packaged.size(), file);
		}
	}
}
```

Comportamento exato:

1. Um `ApplicationRunner` (`bootstrapCatalog`) executado a cada subida; se `hub.bootstrap.enabled=false`, não faz nada.
2. Para cada uma das 5 coleções, **na ordem** `providers`, `tools`, `skills`, `subagents`, `work-contexts`: se `repository.count() > 0` **não toca em nada**; se a coleção está vazia, lê `bootstrap/<arquivo>.json` do classpath e faz `saveAll`, registrando `logger.info("Seeded {} catalog entries from bootstrap/{}.json", n, arquivo)`.
3. O `ObjectMapper` é criado manualmente com `PropertyNamingStrategies.SNAKE_CASE` (para casar os JSONs em `snake_case` com os componentes dos records, ex.: `skill_ids` → `skillIds`).
4. **O MongoDB é a fonte da verdade**: depois da primeira carga, nada aqui atualiza, recria nem ressuscita registros. Editar ou **apagar** um registro no Mongo persiste; para mudar o catálogo, edite as coleções (não os JSONs).
5. Não existe propriedade de sobrescrita (`overwrite`).

## 4. `AgentSkillAccessPolicy` — permissões aplicadas no backend

**Arquivo `src/main/java/com/lamorim/hubdeia/application/orchestration/AgentSkillAccessPolicy.java`** (literal):

```java
package com.lamorim.hubdeia.application.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.Skill;
import com.lamorim.hubdeia.domain.model.Subagent;
import com.lamorim.hubdeia.domain.model.SubagentProvider;
import com.lamorim.hubdeia.domain.model.Tool;
import com.lamorim.hubdeia.domain.repository.SkillRepository;
import com.lamorim.hubdeia.domain.repository.ToolRepository;

@Component
public class AgentSkillAccessPolicy {

	private final SkillRepository skills;
	private final ToolRepository tools;

	public AgentSkillAccessPolicy(SkillRepository skills, ToolRepository tools) {
		this.skills = skills;
		this.tools = tools;
	}

	public List<Skill> skillsFor(Subagent subagent) {
		List<String> ids = subagent.skillIds();
		return ids.isEmpty() ? List.of() : skills.findByIdInAndEnabledTrue(ids);
	}

	public Map<String, AllowedTool> toolsFor(Subagent subagent) {
		List<String> ids = skillsFor(subagent).stream().flatMap(skill -> safeIds(skill.toolIds()).stream()).distinct().toList();
		Map<String, AllowedTool> allowed = new LinkedHashMap<>();
		for (Tool tool : ids.isEmpty() ? List.<Tool>of() : tools.findByIdInAndEnabledTrue(ids)) {
			providerFor(subagent, tool.providerId()).ifPresent(provider -> allowed.put(tool.functionName(), new AllowedTool(tool, provider)));
		}
		return allowed;
	}

	private Optional<SubagentProvider> providerFor(Subagent subagent, String providerId) {
		return subagent.providers().stream()
				.filter(SubagentProvider::enabled)
				.filter(provider -> providerId.equals(provider.providerId()))
				.findFirst();
	}

	private List<String> safeIds(List<String> ids) { return ids == null ? List.of() : ids; }

	public record AllowedTool(Tool tool, SubagentProvider provider) {}
}
```

- `skillsFor(subagent)`: as skills **habilitadas** cujos ids estão em `subagent.skillIds()` (lista vazia → nenhuma consulta).
- `toolsFor(subagent)`: junta (sem repetição) as `toolIds` dessas skills; busca as tools **habilitadas**; e só inclui uma tool se o subagente tem um `SubagentProvider` **habilitado** com `providerId` igual ao da tool. O resultado é um mapa **`functionName` → `AllowedTool(tool, provider)`** (ordem de inserção preservada).
- Consequência: o modelo só recebe `schema` das tools desse mapa, e `SubagentTaskExecutor` recusa qualquer chamada fora dele. Desabilitar uma skill, tool ou o provider do subagente retira a permissão sem reiniciar.

## 5. Como editar o catálogo (sem reiniciar)

O planejador relê `work_contexts`/`subagents` a cada mensagem e o executor lê skills/tools a cada task; portanto qualquer mudança vale a partir da próxima mensagem. Exemplos com `mongosh`:

```text
// adicionar um agente a um contexto
db.work_contexts.updateOne({ _id: "work-context-analise-dados" }, { $addToSet: { agent_ids: "agent-campanhas" } })
// criar um subagente que so escreve (sem tools) a partir de um INSIGHT
db.skills.insertOne({ _id: "skill-briefing-creation", key: "BRIEFING_CREATION", name: "Criacao de briefing", description: "...", instructions: "...", tool_ids: [], enabled: true })
db.subagents.insertOne({ _id: "agent-campanhas", name: "Agente Campanhas", description: "...", system_prompt: "...", skill_ids: ["skill-briefing-creation"], providers: [], produces: ["BRIEFING"], consumes: ["INSIGHT"], enabled: true })
// desligar um contexto
db.work_contexts.updateOne({ _id: "work-context-analise-dados" }, { $set: { enabled: false } })
```

Cuidados: não altere o `_id` de um work context (conversas, artefatos e instâncias apontam para ele); um `agent_ids` com id inexistente é ignorado em silêncio pelo planejador; desligar o contexto ativo de uma conversa faz a próxima tentativa de `KEEP` falhar na validação.

## Armadilhas e decisões não óbvias

1. Seed **por coleção vazia**, não por registro: evita ressuscitar o que foi apagado no banco.
2. O catálogo da semente foi criado para um projeto de dados de marketing/CRM; os textos dos prompts (em português, sem acentos) são parte do produto e devem ser copiados literalmente.
3. `Provider.tools` existe no record mas fica vazio na semente (as tools têm coleção própria).
4. `Tool.input_schema`/`output_schema`/`config` existem no modelo e na semente (vazios), **mas não são usados**: o *schema* real da função está no código Java da `ProviderTool`.
5. A semente não define `Skill`s com tools para o agente de briefing (ele ainda não existe no catálogo); o exemplo da seção 5 é ilustrativo.

## Critérios de aceite

- Com o banco vazio, a primeira subida cria exatamente: 1 provider, 1 tool, 4 skills, 2 subagentes, 1 work context (`BootstrapConfigurationTest`).
- Editar/remover um registro no Mongo e reiniciar **não** o recria nem sobrescreve; `hub.bootstrap.enabled=false` não semeia nada.
- `AgentSkillAccessPolicyTest`: só as tools concedidas por skills **e** por um provider habilitado do subagente aparecem.

## Ordem de implementação sugerida

JSONs → `BootstrapConfiguration` → `AgentSkillAccessPolicy`.
