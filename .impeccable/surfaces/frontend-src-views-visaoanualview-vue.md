---
version: 1
slug: "frontend-src-views-visaoanualview-vue"
primary_target: "frontend/src/views/VisaoAnualView.vue"
related_targets: []
---

# Visão anual (tela principal do app)

Modo: Operate. Público: a família toda (DONO e MEMBRO), de 12 a 70+ anos, no celular e no desktop.
Tarefa: ver o ano mês a mês, entender o peso de cada grupo/categoria e de cada pessoa, e o fluxo de caixa.
Estados que importam: previsto × realizado, mês atual, meses futuros só previstos, ano vazio (família nova),
pendentes para confirmar, recurso fora do plano (402).

## Direction contract

THESIS: A planilha que a família já conhece, viva: grupos que abrem em categorias, 12 meses, total, média, % e rank,
com o previsto separado do realizado. Recusa o dashboard de cartões com número gigante e o "controle do mês".

OWN-WORLD: Areia #F6F4F0 de fundo, barra superior em petróleo #1F5C66 cheio, tabela em papel #FFFDF9 com linhas
pedra #E2DED6, números tabulares em Figtree, despesa em grafite (nunca vermelho), receita em verde-folha, previsto em
coral claro com contorno tracejado. Fraunces só nos títulos de página e no número da simulação.

STORY: A pessoa abre e vê o ano de vocês numa frase (renda, despesas, o que sobra), as 12 barras do ano e a tabela.
Entende onde o dinheiro vai e quanto cada coisa pesa no ano; confere o que está previsto e lança o que falta.

FIRST VIEWPORT: Barra petróleo (marca, navegação, Lançar). Título "O ano de vocês" + seletor de ano + abas
Orçamento / Fluxo de caixa / Por pessoa. Uma frase-resumo. A copa do ano: 12 barras alinhadas às colunas dos meses
(despesa realizada cheia, prevista tracejada, traço verde da renda). Logo abaixo, a tabela com cabeçalho e 1ª coluna
fixos. No celular, as 12 barras viram o seletor de mês e a tabela vira lista do mês escolhido.

FORM: Planilha viva, 1ª da lista ordenada (estrutura sorteada 3 de 3, escolhida pelo usuário). seed:b10b0b73e13f8685

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Decisões em aberto
- Logo final (a copa do ano) ainda com designer: o front usa um símbolo provisório de 12 barras desenhado em SVG.
