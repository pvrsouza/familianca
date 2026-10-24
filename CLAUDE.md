# Familiança

Antes de desenvolver, leia as regras e os pontos de atenção em `projeto/projeto.md` e o lado técnico no `README.md`.

## Discutir a solução antes de implementar

Todo pedido de desenvolvimento (regra nova, funcionalidade, mudança de comportamento) começa com a skill `/grill-me`:
antes de escrever código, conversar sobre a solução proposta e **criticá-la de verdade**. Isso inclui riscos, casos de
borda, conflito com regras de `projeto/projeto.md`, alternativas mais simples, custo e o que pode dar errado. Uma
pergunta por vez, com a recomendação de quem critica. Só implementar depois de fechar as decisões com quem pediu.
Se a skill não estiver instalada, fazer a mesma conversa sem ela. Ficam de fora tarefas sem decisão de solução (rodar
testes, commit, push, dúvida pontual).

## Segurança em todo desenvolvimento

Toda mudança (backend, front, infra) passa pelo checklist de `docs/seguranca.md` ("Checklist para cada
desenvolvimento"), que cobre os 13 requisitos mínimos, e aplica os itens pertinentes. Ao entregar, diga quais itens
se aplicaram e como foram atendidos. O que ficar para depois entra no plano do mesmo arquivo.

## Documentação em toda mudança

Toda mudança ou incremento atualiza a documentação no mesmo commit (ou antes de entregar): regras de negócio e
decisões em `projeto/projeto.md` (com a data), visual e componentes em `DESIGN.md`, lado técnico (endpoints, setup,
deploy) no `README.md`. Ao entregar, diga o que foi documentado.

## Commits

Nunca colocar o Claude (nem qualquer IA) como coautor: nada de `Co-Authored-By`, `Generated with` ou outro trailer de
atribuição na mensagem do commit ou na descrição do PR. O autor é só a identidade git de quem desenvolve.
