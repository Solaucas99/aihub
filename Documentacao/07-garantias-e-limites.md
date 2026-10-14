# 7. Garantias e limites

Um retrato honesto do que o Hub garante hoje e do que ainda não faz.

## O que o sistema garante

| Garantia | Como |
|---|---|
| **Nada inventado pelo planejador** | Números, tabelas e gráficos só saem de especialistas; o prompt do planejador proíbe e o Hub só aceita tarefas para especialistas válidos |
| **Decisão inválida não é gravada** | O tratador valida contexto e especialistas **antes** de gravar qualquer coisa |
| **Resultado conferido** | Toda resposta passa pelo validador; até 3 tentativas; HITL para o que depende do usuário |
| **Uma saída por execução** | A execução sai de `RUNNING` uma única vez, por atualização condicional; resultados tardios são descartados |
| **Ordem coerente de planejamento** | Mensagens da mesma conversa são planejadas uma a uma, na ordem de chegada |
| **Dependências respeitadas** | Quem consome um resultado espera quem o produz; se o produtor falha, o consumidor é pulado |
| **Sem duplicidade de versão** | Artefatos têm índice único (conversa, tipo, versão) e novas tentativas na concorrência |
| **A conversa não trava** | Resposta imediata, processamento em segundo plano, perguntas pendentes não bloqueiam |
| **Falha visível** | Erros de tarefa ou de execução levam a `FAILED` e a uma mensagem ao usuário, nunca a silêncio |
| **Sem acoplamento visual** | O conteúdo visual é neutro e montado em tempo de execução |

## Como a qualidade foi verificada

- **72 testes automatizados** (5 são opt-in e só rodam quando habilitados explicitamente contra o modelo real), incluindo concorrência de publicação de artefatos, ordenação de memória, decisões do planejador, validação e visuais.
- Um teste **opcional contra o modelo real** do planejador, que reproduz o caso "analise, depois gere gráficos" e verifica que o pedido volta ao mesmo especialista.
- Os documentos em `specs/`, `Modelling/` e `Mermaid/` permitem reconstruir e **conferir mecanicamente** o projeto.

## Limites conhecidos

| Limite | Efeito prático | Tratamento sugerido |
|---|---|---|
| **Sem autenticação real**: o usuário é identificado pelo cabeçalho `Authorization` | Qualquer cliente que envie um identificador age como esse usuário | Integrar um provedor de identidade antes de expor fora da rede interna |
| **Sem recuperação de execuções órfãs** | Se o Hub cair no meio de uma execução, ela fica `RUNNING` | Rotina de varredura que marca como `FAILED` execuções antigas |
| **Polling (sem SSE/WebSocket)** | O andamento chega com atraso de alguns instantes | Evoluir para eventos em tempo real |
| **Planejador sem o contexto por execução** | Com vários contextos, a escolha entre `KEEP` e `ACTIVATE` pode errar | Incluir o contexto usado em cada execução recente no que o planejador lê |
| **Um único provider (Genie)** | Só dados da Genie | Implementar outros `ProviderTool` (veja [Extensibilidade](06-extensibilidade.md)) |
| **Sem tela de administração do catálogo** | Mudanças de catálogo são feitas no banco | Criar uma interface de gestão |
| **Dependência de modelos de linguagem** | Latência e custo variam; julgamentos podem divergir | Monitorar custo e taxa de RETRY/HITL; avaliações periódicas |
| **Corrida de exibição (pequena)** | A execução pode terminar um instante antes das mensagens finais aparecerem | A tela recarrega a conversa cerca de 700 ms depois do status final |
| **Suíte completa com MongoDB não reexecutada na réplica** | A reconstrução a partir das specs foi verificada por compilação e conferência estrutural, não pelos 72 testes com banco | Rodar `./mvnw test` com o MongoDB no ar após reconstruir |

## Riscos para decisão

1. **Exposição**: sem autenticação real, não publique fora de uma rede controlada.
2. **Custo**: cada pergunta que delega usa vários modelos (planejador, especialistas, validador e comunicador) e pode repetir tentativas; defina limites e monitore.
3. **Qualidade dos dados**: o Hub confere coerência, não a **veracidade** da fonte; erros na Genie chegam como se fossem verdade se parecerem plausíveis.

Próximo: [Glossário](08-glossario.md).
