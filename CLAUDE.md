# Familiança

Antes de desenvolver, leia as regras e os pontos de atenção em `projeto/projeto.md` e o lado técnico no `README.md`.

## Discutir a solução antes de implementar

Todo pedido de desenvolvimento (regra nova, funcionalidade, mudança de comportamento) começa com a skill `/grill-me` (plugin mattpocock-skills; ela só chama a skill `grilling`, que é a que a IA
aciona sozinha):
antes de escrever código, conversar sobre a solução proposta e **criticá-la de verdade**. Isso inclui riscos, casos de
borda, conflito com regras de `projeto/projeto.md`, alternativas mais simples, custo e o que pode dar errado. Uma
pergunta por vez, com a recomendação de quem critica. Só implementar depois de fechar as decisões com quem pediu.
Se a skill não estiver instalada, fazer a mesma conversa sem ela. Ficam de fora tarefas sem decisão de solução (rodar
testes, commit, push, dúvida pontual).

## Segurança em todo desenvolvimento

Toda mudança (backend, front, infra) passa pelo checklist de `docs/seguranca.md` ("Checklist para cada
desenvolvimento"), que cobre os 13 requisitos mínimos, e aplica os itens pertinentes. Ao entregar, diga quais itens
se aplicaram e como foram atendidos. O que ficar para depois entra no plano do mesmo arquivo.

## LGPD em todo desenvolvimento

O estado da LGPD fica em `.lgpd/` (plugin `lgpd-skills`; o orquestrador é a skill `lgpd-skills:lgpd-audit`). Mudança
que coleta dado pessoal novo, muda finalidade, adiciona fornecedor ou mexe em retenção atualiza `data-map.md`,
`legal-basis.md` e `gaps.md` no mesmo commit; mudança material na política gera versão nova em `.lgpd/policies/`.

## Documentação em toda mudança

Toda mudança ou incremento atualiza a documentação no mesmo commit (ou antes de entregar): regras de negócio e
decisões em `projeto/projeto.md` (com a data), visual e componentes em `DESIGN.md`, lado técnico (endpoints, setup,
deploy) no `README.md`. Ao entregar, diga o que foi documentado.

## Listar as pendências no início da sessão

Na primeira resposta de cada sessão, antes de qualquer outra coisa, listar as pendências disponíveis (de
"Pendências e planos futuros" e dos aprendizados em `projeto/projeto.md` e do plano de `docs/seguranca.md`),
numeradas, com o tamanho (P, M, G) e uma sugestão de por onde começar, para quem pediu escolher.

## Revisar as pendências ao terminar

Ao fim de toda implementação, revisar "Pendências e planos futuros" (e os aprendizados) em `projeto/projeto.md` e o
plano de `docs/seguranca.md`: o que a mudança resolveu sai da lista no mesmo commit (ou é marcado como feito, com a
data), e o que ela deixou para depois entra. Na entrega, dizer o que foi tirado ou incluído e listar as pendências
que continuam disponíveis para a próxima funcionalidade, para quem pediu escolher.

## Revisar o modelo de negócio a cada entrega

O Familiança vai virar startup. Ao fim de toda entrega (depois de revisar as pendências), rodar a skill
`/iteracao-negocio`: ligar o que foi entregue às hipóteses de `projeto/negocio.md`, criticar a mais arriscada e
registrar a iteração no mesmo arquivo. Na entrega, dizer o que mudou no modelo de negócio e a próxima pergunta a
testar. Ficam de fora as mesmas tarefas sem decisão de solução.

## Manter o harness de IA atualizado

O harness é o que orienta a IA neste projeto: este `CLAUDE.md`, `.claude/settings.json` (plugins), as skills em
`.claude/skills/` (hoje `iteracao-negocio`), `.agents/brand-context.md`, `PRODUCT.md` e `projeto/negocio.md`. Toda
mudança ou implementação atualiza o harness no mesmo commit quando mexe no que ele descreve: plugin ou skill nova (ou
removida), regra nova de como trabalhar, mudança de fluxo de entrega, recurso que muda produto, marca ou modelo de
negócio. Skill citada aqui tem que existir com esse nome. Ao entregar, dizer o que foi atualizado no harness (ou
"harness sem mudança").

## Commits

Nunca colocar o Claude (nem qualquer IA) como coautor: nada de `Co-Authored-By`, `Generated with` ou outro trailer de
atribuição na mensagem do commit ou na descrição do PR. O autor é só a identidade git de quem desenvolve.
