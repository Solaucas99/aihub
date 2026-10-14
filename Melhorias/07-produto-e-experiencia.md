# 7. Produto e experiência

**Tese:** a arquitetura é convincente para quem é técnico; **a confiança do usuário de negócio vem do que ele vê na tela**. Os itens abaixo transformam o rigor interno (validação, fonte, versões) em algo que o usuário percebe.

## Estado atual (verificado no código)

- Chat estático em estilo "Claude UI": lista de conversas, tema claro/escuro, markdown sanitizado, gráficos Vega-Lite, painel de artefatos, indicação de andamento por polling (2,2 s).
- O usuário vê **o que** foi respondido, mas não **como** nem **a partir de quê**.
- Perguntas pendentes (HITL) aparecem como bloco "Ainda preciso de você" dentro da resposta; o usuário precisa digitar a resposta por extenso.
- Não há cancelamento, nem feedback, nem exportação.
- O id do usuário e o endereço da API são campos editáveis na tela (útil em desenvolvimento, inadequado em produção; veja [SEG-1](04-seguranca-e-governanca.md)).

---

### PRD-1 · "Como cheguei nisso"
**P1 · M · Alto**

Painel recolhível em cada resposta: especialista consultado, fonte e consulta executada, data/hora dos dados, tentativas e vereditos (sem expor prompts internos). Alimentado por [OBS-5](01-observabilidade.md) e [QLD-7](03-qualidade-e-avaliacao-de-ia.md). É o recurso que mais reduz a pergunta "posso confiar nesse número?".

### PRD-2 · Feedback 👍/👎 com motivo
**P1 · S · Alto**

Por resposta (ligado ao `run_id`), com motivos pré-definidos ("número errado", "não respondeu", "demorou", "outro"). Gera a fila de [QLD-6](03-qualidade-e-avaliacao-de-ia.md) e o indicador de satisfação do [case de sucesso](09-case-de-sucesso.md).

### PRD-3 · Cancelar, editar e retomar
**P1 · M · Médio**

Botão "Parar" durante a execução ([CNF-7](05-confiabilidade-e-escala.md)); editar a última pergunta e refazer; mensagem clara quando a execução foi interrompida por reinício (com botão "tentar de novo").

### PRD-4 · HITL com respostas rápidas
**P2 · S · Médio**

Quando a pergunta pendente é fechada ("Business, Individual ou ambos?"), exibir **botões** com as opções e um indicador de pendência no cabeçalho da conversa. Hoje o validador já escreve a pergunta; basta o modelo sugerir as opções num campo estruturado.

### PRD-5 · Exportar e compartilhar
**P2 · M · Médio**

Exportar a resposta/artefato em PDF, CSV (tabelas) e imagem (gráficos); link de leitura somente para colegas autorizados. Reaproveita o conteúdo visual neutro ([EXT-7](02-extensibilidade.md)).

### PRD-6 · Descoberta: "o que posso perguntar?"
**P2 · S · Médio**

Sugestões iniciais e exemplos derivados do **catálogo** (as `description` dos especialistas e contextos), com chips clicáveis. Reduz a página em branco e ensina o usuário a pedir bem.

### PRD-7 · Acessibilidade e internacionalização
**P2 · M · Médio**

Navegação por teclado, contraste (a paleta claro/escuro já existe), leitores de tela (gráficos com descrição textual), textos fora do código para i18n e layout móvel.

---

## Jornada de uso que o produto deve ganhar com estas melhorias

1. O usuário pergunta e vê, em segundos, **o que o Hub está fazendo** (etapas em tempo real).
2. Recebe a resposta com **texto, tabela e gráfico** e um link "Como cheguei nisso".
3. Pede "agora só os jovens" e o Hub **continua com o mesmo especialista**, com o resultado anterior como base.
4. Quando falta uma informação, responde com **um toque**.
5. Avalia a resposta e, se quiser, **exporta**.

Próximo: [Engenharia e entrega](08-engenharia-e-entrega.md).
