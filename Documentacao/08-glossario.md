# 8. Glossário

| Termo | Significado |
|---|---|
| **Hub de IA** | O sistema descrito nesta documentação |
| **Planejador** | Modelo de linguagem que decide se responde, pergunta ou delega, e escreve as tarefas |
| **Especialista / subagente** | Modelo de linguagem com perfil, habilidades e ferramentas próprias, que consulta dados e produz uma análise |
| **Validador** | Modelo de linguagem "juiz" que classifica cada resposta como `CONTINUE`, `RETRY`, `HITL` ou `CANCEL` |
| **Comunicador** | Modelo de linguagem que escreve a introdução da resposta final |
| **Execução (AgentRun)** | O trabalho realizado para atender uma pergunta que exigiu delegação; estados `RUNNING`, `FINISHED`, `CANCELLED`, `FAILED` |
| **Tarefa (Task)** | Uma unidade de trabalho de um especialista dentro de uma execução; estados `NEW`, `RUNNING`, `FINISHED`, `CANCELLED`, `FAILED` |
| **WorkContext (contexto de trabalho)** | "Modo" do catálogo que agrupa especialistas e instruções; no máximo um ativo por conversa |
| **Instância de contexto** | O contexto de catálogo dentro de uma conversa: `ACTIVE` ou `PAUSED` |
| **Skill (habilidade)** | Conjunto de instruções e ferramentas que um especialista pode usar |
| **Tool (ferramenta)** | Função que o especialista chama (ex.: consultar a Genie) |
| **Provider** | Conector de uma fonte de dados externa (hoje, a Genie) |
| **Genie** | Serviço do Databricks que responde perguntas sobre dados em linguagem natural |
| **Artefato** | Resultado aprovado, guardado e versionado; `ACTIVE` ou `SUPERSEDED`, com marca `stale` |
| **produces / consumes** | Tipos de resultado que um especialista produz ou precisa receber; o Hub usa isso para ordenar tarefas |
| **HITL (human in the loop)** | Quando falta uma decisão do usuário; vira uma pergunta pendente que não bloqueia a conversa |
| **Retry** | Nova tentativa de uma tarefa com o feedback do validador (até 3) |
| **Memória curta** | As últimas 5 tarefas de cada especialista na conversa |
| **Visual** | Gráfico (Vega-Lite) ou tabela (HTML) em formato neutro, montado em tempo de execução |
| **Bootstrap** | Gravação do catálogo inicial na primeira subida, só em coleções vazias |
| **Polling** | A tela perguntar ao Hub, a intervalos curtos, o status da execução |
| **LLM** | Modelo de linguagem (aqui, da OpenAI) |
| **Vega-Lite** | Formato declarativo de gráficos usado como conteúdo visual neutro |
