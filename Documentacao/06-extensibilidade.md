# 6. Extensibilidade

A pergunta prática: **se eu quiser criar um agente novo (por exemplo, um que monte o Briefing), é fácil?** Depende de o agente precisar de uma **nova fonte de dados** ou não.

## Resumo

| Quero… | Esforço | Precisa de código? |
|---|---|---|
| Novo especialista usando a Genie (outro espaço de dados, outro foco) | **Baixo** — são documentos no MongoDB | Não |
| Novo contexto de trabalho agrupando especialistas | **Baixo** — um documento no MongoDB | Não |
| Mudar instruções de um especialista, habilidade ou contexto | **Baixo** — editar o documento | Não |
| Novo tipo de resultado reaproveitável (artefato) | **Baixo** — declarar `produces`/`consumes` nos especialistas | Não |
| Conectar uma **nova fonte de dados** (outra API, outro banco) | **Médio** — uma classe Java | Sim, uma classe |
| Mudar como o planejador decide | Médio — ajustar o prompt e o esquema | Sim |

## Criar um especialista só com dados (sem código)

Um especialista é o resultado de quatro peças do catálogo, todas documentos no MongoDB:

```
work_context ──agent_ids──▶ subagent ──skill_ids──▶ skill ──tool_ids──▶ tool ──provider_id──▶ provider
                                └── providers[] (provider habilitado + configuração, ex.: space_id da Genie)
```

Passos para um "Agente Briefing" que usa a Genie:

1. **Skill**: documento em `skills` (chave única, nome, instruções, `tool_ids` apontando para a ferramenta da Genie).
2. **Subagent**: documento em `subagents` com `name`, `description` (é o que o planejador lê para decidir quando usá-lo), `system_prompt`, `skill_ids`, `produces` (ex.: `["BRIEFING"]`), `consumes` (ex.: `["INSIGHT"]` se o briefing parte de uma análise) e `providers` com o provider habilitado e sua configuração.
3. **Work context**: adicione o id do novo especialista em `agent_ids` de um contexto existente (ou crie um contexto novo).

Pronto: no próximo pedido o planejador já enxerga o novo especialista e o catálogo declara a ordem — **se `BRIEFING` consome `INSIGHT`, o Hub ordena as tarefas sozinho** (análise primeiro, briefing depois, com o resultado novo) sem o planejador declarar dependências.

Pontos de atenção:
- O bootstrap **só grava em coleções vazias**; para um sistema já em uso, a alteração é feita diretamente no banco (scripts de `Modelling/` mostram a forma dos documentos).
- A ferramenta só fica disponível ao especialista se **a habilidade, a ferramenta e o provider estiverem habilitados** e o especialista tiver aquele provider.
- A qualidade do novo especialista depende principalmente do `system_prompt`, da `description` e das habilidades.

## Conectar uma nova fonte de dados (uma classe)

O contrato é pequeno:

```java
public interface ProviderTool {
    String name();                       // nome da função, igual ao function_name da ferramenta no catálogo
    Document schema();                   // esquema dos argumentos que o modelo pode enviar
    ProviderToolResult execute(ProviderToolContext context, Document arguments);
}
```

1. Implemente `ProviderTool` como componente Spring. O registro (`ProviderToolRegistry`) encontra todas as ferramentas pelo `name()`.
2. Devolva um `ProviderToolResult` com o **texto**, uma **saída estruturada** opcional, a **sessão do provider** (para continuar conversas) e **visuais neutros** (HTML ou Vega-Lite).
3. Cadastre no catálogo o `provider`, a `tool` (com o mesmo `function_name`) e vincule às habilidades.

Nada no planejador, no validador, no comunicador nem na tela precisa mudar: o conteúdo visual é neutro e o restante só conhece o contrato `ProviderTool`.

## Dívida e armadilhas conhecidas

- O planejador **não sabe qual contexto cada execução usou** quando há vários contextos; ele distingue `KEEP` de `ACTIVATE` pelo contexto ativo. Com um único contexto (hoje) isso não aparece.
- Não há tela de administração do catálogo: a edição é no banco.
- Há um único provider (Genie); o contrato foi desenhado para mais, mas só um foi exercitado.

Próximo: [Garantias e limites](07-garantias-e-limites.md).
