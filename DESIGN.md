---
name: Familiança
description: A planilha que a família já conhece, viva. Calor na cor e no texto, rigor nos números.
colors:
  petroleo: "#1f5c66"
  petroleo-escuro: "#174850"
  petroleo-noite: "#123a41"
  petroleo-tinta: "#e4eeef"
  petroleo-tinta-forte: "#cfe0e2"
  foco: "#2b7f8c"
  coral: "#e07a5f"
  coral-claro: "#fce7e0"
  coral-borda: "#c4573d"
  coral-sobre-escuro: "#f4b5a3"
  verde: "#2f7a5b"
  verde-claro: "#e3f0e9"
  erro: "#b42318"
  erro-claro: "#fdecea"
  areia: "#f6f4f0"
  areia-funda: "#efece6"
  papel: "#fffdf9"
  pedra: "#e2ded6"
  pedra-forte: "#cfc9be"
  grafite: "#1e2a2e"
  cinza: "#5c6b6e"
  cinza-claro: "#8a9597"
typography:
  display:
    fontFamily: "Fraunces, ui-serif, Georgia, 'Times New Roman', serif"
    fontSize: "clamp(2.75rem, 6vw, 4.5rem)"
    fontWeight: 500
    lineHeight: 1
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Fraunces, ui-serif, Georgia, 'Times New Roman', serif"
    fontSize: "1.75rem"
    fontWeight: 500
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  title-serif:
    fontFamily: "Fraunces, ui-serif, Georgia, 'Times New Roman', serif"
    fontSize: "1.375rem"
    fontWeight: 500
  title:
    fontFamily: "'Figtree Variable', Figtree, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 650
    lineHeight: 1.3
  body:
    fontFamily: "'Figtree Variable', Figtree, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  numero:
    fontFamily: "'Figtree Variable', Figtree, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    fontFeature: "'tnum'"
  label:
    fontFamily: "'Figtree Variable', Figtree, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
    letterSpacing: "0.04em"
rounded:
  raio: "8px"
  raio-g: "12px"
  pilula: "999px"
spacing:
  e-1: "0.25rem"
  e-2: "0.5rem"
  e-3: "0.75rem"
  e-4: "1rem"
  e-5: "1.25rem"
  e-6: "1.5rem"
  e-8: "2rem"
  e-10: "2.5rem"
  e-12: "3rem"
components:
  botao-primario:
    backgroundColor: "{colors.petroleo}"
    textColor: "#ffffff"
    rounded: "{rounded.raio}"
    padding: "0 1rem"
    height: "2.75rem"
  botao-primario-hover:
    backgroundColor: "{colors.petroleo-escuro}"
  botao:
    backgroundColor: "{colors.papel}"
    textColor: "{colors.grafite}"
    rounded: "{rounded.raio}"
    padding: "0 1rem"
    height: "2.75rem"
  botao-hover:
    backgroundColor: "{colors.areia-funda}"
  botao-sutil:
    backgroundColor: "transparent"
    textColor: "{colors.petroleo}"
    rounded: "{rounded.raio}"
    padding: "0 1rem"
    height: "2.75rem"
  botao-sutil-hover:
    backgroundColor: "{colors.petroleo-tinta}"
  botao-pequeno:
    rounded: "{rounded.raio}"
    padding: "0 0.75rem"
    height: "2.25rem"
  entrada:
    backgroundColor: "#ffffff"
    textColor: "{colors.grafite}"
    rounded: "{rounded.raio}"
    padding: "0 0.75rem"
    height: "2.75rem"
  painel:
    backgroundColor: "{colors.papel}"
    rounded: "{rounded.raio-g}"
    padding: "1.25rem"
  selo:
    backgroundColor: "{colors.areia-funda}"
    textColor: "{colors.cinza}"
    rounded: "{rounded.pilula}"
    padding: "0.125rem 0.5rem"
  selo-previsto:
    backgroundColor: "{colors.coral-claro}"
    textColor: "{colors.grafite}"
    rounded: "{rounded.pilula}"
    padding: "0.125rem 0.5rem"
  selo-verde:
    backgroundColor: "{colors.verde-claro}"
    textColor: "{colors.verde}"
    rounded: "{rounded.pilula}"
  barra-superior:
    backgroundColor: "{colors.petroleo}"
    textColor: "{colors.areia}"
    height: "3.5rem"
  aviso:
    backgroundColor: "{colors.petroleo-tinta}"
    textColor: "{colors.petroleo-noite}"
    rounded: "{rounded.raio}"
    padding: "0.75rem 1rem"
---

# Design System: Familiança

## Overview

**Creative North Star: "A planilha da mesa da cozinha"**

A Familiança é a planilha que a família já conhece, só que viva: grupos que abrem em categorias, doze meses lado a lado, total, média, % e rank. O mundo visual é papel claro sobre areia quente, com uma barra de petróleo cheio em cima e a tabela como protagonista. O calor mora na cor (areia, coral), no serifado macio da Fraunces e no texto; o rigor mora nos números tabulares, nas linhas de pedra finas e no alinhamento exato das colunas.

A densidade é de planilha: linhas de 2,375rem, números em 0,875rem, cabeçalho e primeira coluna fixos. Ao redor da tabela, porém, há espaço para respirar (seções separadas por 2,5rem, página com 2rem de topo). A linguagem visual mais distintiva é o par **previsto × realizado**: o realizado é petróleo cheio (ou grafite, no texto); o previsto é coral claro com contorno tracejado, legível até impresso em preto e branco. Esse par aparece nas barras da "copa do ano", nas células da tabela, nos selos, no logo e na ilustração do estado vazio.

A interface recusa o dashboard de cartões com número gigante: o número grande em Fraunces existe uma vez, na simulação de "Quanto pesa". Despesa nunca é vermelha.

**Key Characteristics:**
- Fundo areia, superfícies em papel, divisórias em pedra; petróleo como cor dominante da marca.
- Previsto = coral claro + tracejado; realizado = petróleo cheio. Sempre os dois juntos, nunca só cor.
- Figtree para interface e números (algarismos tabulares); Fraunces para títulos de página, de painel e para o número da simulação.
- Plano por padrão: bordas de 1px em pedra, cantos de 8px/12px, sombra só no que flutua.
- Ícones Lucide de linha, traço 1,75px.

## Colors

Uma base fria e serena (petróleo) sobre neutros quentes (areia, papel, pedra), com o coral em doses pequenas para o que ainda está por vir.

### Primary
- **Petróleo** (`petroleo`): a cor da marca. Barra superior cheia, botão primário, links, barras do realizado na copa do ano, caixa de seleção, cursor de texto, aba ativa no celular.
- **Petróleo profundo** (`petroleo-escuro`): hover do primário e de links, texto de destaque pequeno (aba segmentada ativa, mês atual no cabeçalho, número da simulação), selos petróleo, barra realçada na copa.
- **Petróleo noite** (`petroleo-noite`): texto sobre tintas de petróleo (avisos, seleção), avatar na barra, avisos rápidos (toasts) e o tom do backdrop e das sombras.
- **Tinta de petróleo** (`petroleo-tinta`, `petroleo-tinta-forte`): fundos de aviso informativo, hover do botão sutil, coluna do mês atual e linha realçada na tabela, seleção de texto, barras do estado vazio.
- **Foco** (`foco`): anel de foco de 2px e borda de campo focado.

### Secondary
- **Coral** (`coral`): o acento quente. Na interface aparece cheio só no logo (barras previstas da copa). Nunca como texto sobre fundo claro.
- **Coral claro** (`coral-claro`): fundo de tudo que é previsto (células, barras, selos, valores).
- **Borda coral** (`coral-borda`): o contorno tracejado de 1px do previsto.
- **Coral sobre escuro** (`coral-sobre-escuro`): o previsto quando o fundo é petróleo (logo claro, gráfico da tela de acesso).

### Tertiary
- **Verde-folha** (`verde`, `verde-claro`): o que entra. Texto de receita, título do bloco de receitas, traço da renda sobre as barras do ano, selo verde e aviso de sucesso.

### Neutral
- **Areia** (`areia`): fundo das telas; linhas de tipo e de saldo na tabela; hover de itens de menu; texto sobre a barra petróleo.
- **Areia funda** (`areia-funda`): cabeçalho das tabelas, trilho dos segmentados, hover do botão secundário, selo neutro, campo desabilitado, esqueleto de carregamento.
- **Papel** (`papel`): superfícies (painéis, planilha, painel lateral, menu, barra inferior no celular).
- **Pedra** (`pedra`): todas as divisórias e bordas de superfície (1px).
- **Pedra forte** (`pedra-forte`): borda de botão e campo, separador entre blocos da tabela (2px), barra fantasma do ano vazio, barra de rolagem.
- **Grafite** (`grafite`): texto principal e **despesas**.
- **Cinza-petróleo** (`cinza`): texto secundário, legendas, rótulos de coluna.
- **Cinza claro** (`cinza-claro`): placeholder, hover de borda de campo, linhas inativas.
- **Erro** (`erro`, `erro-claro`): só erros reais (campo inválido, botão de perigo, sobra negativa do mês), sempre com ícone e texto.

### Named Rules
**A Regra da Despesa Sem Culpa.** Despesa é grafite, nunca vermelha. O vermelho de erro fica para erro de sistema e alerta real (as despesas passaram da renda), sempre com ícone e texto.

**A Regra do Coral Que Não Alarma.** Coral significa "previsto" (e conquista). Nunca entra em mensagem de erro e nunca é texto sobre fundo claro; o texto sobre coral claro é grafite.

**A Regra do Petróleo Dominante.** Petróleo é a cor dominante de qualquer tela; o coral entra em doses pequenas, quase sempre como fundo claro tracejado.

### Tema escuro
Escolhido em Minha conta (Automático, que segue o sistema, Claro ou Escuro), vale só no aparelho. Cada token de cor tem o par claro/escuro em `light-dark()` no `base.css`; o `color-scheme` do `<html>` decide qual vale e a impressão sai sempre no claro.

- A base é petróleo noite, nunca preto: fundo `#0f1a1c`, papel `#182628`, areia funda `#213134`; texto em areia (`#ebe7e0`).
- Os nomes continuam os mesmos e trocam de papel com o tema: `petroleo-escuro` e `petroleo-noite` viram tons claros (são texto de destaque), `coral-claro` vira tinta coral escura com o tracejado mais claro. O par previsto × realizado e as regras acima continuam valendo.
- O que é petróleo cheio nos dois temas (barra superior, lado da marca na tela de acesso, aviso rápido) usa `barra-fundo`, `noite-fixa` e `sobre-escuro`, não os tokens que trocam.
- O QR do MFA fica sempre em fundo branco, para o autenticador ler.

## Typography

**Display Font:** Fraunces (com ui-serif, Georgia), pesos 500 e 600 carregados
**Body Font:** Figtree Variable (com ui-sans-serif, system-ui)
**Label/Mono Font:** Figtree com algarismos tabulares

**Character:** Uma serifa macia de livro de receitas de família nos títulos, e uma sem serifa arredondada, muito legível, que carrega a interface e todos os números. Nunca mais de duas famílias.

### Hierarchy
- **Display** (Fraunces 500, clamp(2.75rem, 6vw, 4.5rem), 1): o número da simulação em "Quanto pesa", em petróleo profundo. Uma vez por tela, só ali.
- **Headline** (Fraunces 500, 1,75rem, 1.15, -0.01em, `text-wrap: balance`): o título de página (h1). Cai para 1,375rem abaixo de 760px. O lema da tela de acesso usa 2,25rem.
- **Title serifado** (Fraunces 500, 1,375rem): títulos do painel lateral, do resumo do mês no celular e do bloco de começo do ano vazio.
- **Title** (Figtree 650, 1,125rem, 1.3): títulos de seção (h2); h3 em 1rem, 650.
- **Body** (Figtree 400, 1rem, 1.5): texto corrido; textos de apoio limitados a 60ch (cabeçalho da página) ou 46ch (estado vazio). Texto de apoio em 0,875rem.
- **Número** (Figtree, 0,875rem, `tabular-nums` + `tnum`): toda tabela e todo valor monetário. Totais e linhas de saldo em 700; grupos em 650.
- **Label** (Figtree 600, 0,8125rem, 0.04em a 0.05em, caixa alta): cabeçalhos de coluna da planilha (meses), linha de tipo (RECEITAS/DESPESAS), títulos de coluna e de bloco curtos. Rótulos de campo em 0,875rem, 600, sem caixa alta.

### Named Rules
**A Regra dos Centavos Alinhados.** Todo número que se compara com outro usa algarismos tabulares. Sem exceção em tabelas, campos de valor e resumos.

**A Regra da Serifa Rara.** Fraunces só em títulos (página, painel, resumo do mês), no logotipo e no número da simulação. Rótulos, botões, tabelas e textos são Figtree.

## Layout

A página tem largura máxima de 80rem, centralizada, com respiro de 2rem/1,5rem/3rem (topo/lados/base); abaixo de 760px vira 1,25rem/1rem e ganha espaço extra na base para a barra de navegação inferior. O ritmo de espaço é de base 4px (`e-1` a `e-12`); o vão padrão entre itens é 0,5rem a 1rem, entre campos de formulário 1,25rem, entre seções 2,5rem.

A barra superior é fixa (sticky), em petróleo cheio, com 3,5rem de altura: marca, navegação, botão Lançar e usuário. A navegação tem seis itens (Categorias inclusive, fora do menu do usuário). Abaixo de 1160px o nome do usuário, o nome da marca e o texto do Lançar somem; abaixo de 900px a navegação vira uma barra inferior em papel com itens de ícone + rótulo (alvo mínimo de 3,75rem).

A planilha anual é a superfície principal: rola nos dois eixos dentro de um contêiner com altura limitada à janela, com cabeçalho dos meses e primeira coluna fixos (sticky), largura mínima de 81rem e colunas de largura fixa (nome 14rem, mês 4,25rem, total 5,75rem). Acima dos meses fica a "copa do ano": doze barras alinhadas exatamente às colunas. No celular, as doze barras viram o seletor de mês e a tabela vira lista do mês escolhido.

Formulários abrem num painel lateral à direita (30rem); abaixo de 640px ele vira uma folha que sobe de baixo. Grades de dois campos viram uma coluna abaixo de 520px. Toque mínimo de 2,75rem em botões e campos (2,25rem no botão pequeno).

## Elevation & Depth

O sistema é plano. A profundidade vem de camadas tonais (areia → papel → areia funda nos cabeçalhos) e de bordas de 1px em pedra. Sombra aparece só no que flutua sobre a página: menu, painel lateral, aviso rápido, barra inferior do celular, e o "chip" ativo dos segmentados. As sombras são difusas e tingidas de petróleo noite ou grafite, nunca pretas.

### Shadow Vocabulary
- **Painel lateral** (`box-shadow: -8px 0 32px rgb(18 58 65 / 0.18)`): o painel que entra pela direita, com backdrop `rgb(18 58 65 / 0.32)`.
- **Menu suspenso** (`box-shadow: 0 8px 24px rgb(18 58 65 / 0.16)`): menu do usuário.
- **Aviso rápido** (`box-shadow: 0 6px 20px rgb(18 58 65 / 0.24)`): toast em pílula petróleo noite.
- **Barra inferior** (`box-shadow: 0 -2px 12px rgb(30 42 46 / 0.06)`): navegação do celular.
- **Segmento ativo** (`box-shadow: 0 1px 2px rgb(30 42 46 / 0.12)`): a aba escolhida assenta em papel dentro do trilho.

### Named Rules
**A Regra do Papel Plano.** Superfícies em repouso não têm sombra: painéis, planilha e tabelas são papel com borda de pedra. Sombra é só para o que flutua.

## Shapes

Cantos suavizados, nunca retos nem exagerados: 8px (`raio`) para botões, campos, avisos e itens de menu; 12px (`raio-g`) para painéis, planilha, tabelas e menus; pílula (999px) para selos e avisos rápidos; círculo para avatares. Os segmentados usam o raio de 8px com trilho de 11px (8px + 3px de recuo). As barras do ano são arredondadas só no topo (4px) e quase retas na base (2px), como colunas de um gráfico. A célula prevista ganha 6px de canto e o contorno tracejado recuado 4px dentro da célula.

O tracejado é a forma do "ainda não aconteceu": contorno de 1px em `coral-borda` no previsto, 1,5px em `pedra-forte` na barra fantasma do ano vazio. Quando o previsto é confirmado, a célula perde o tracejado e assenta no papel (transição de 450ms).

## Components

### Buttons
Firmes e calmos, sem brilho: a cor muda, o botão não pula.
- **Shape:** cantos suaves (8px), altura mínima de 2,75rem, borda de 1px, Figtree 600 em 0,875rem, ícone de 1,125rem com 0,5rem de vão.
- **Primário:** petróleo cheio com texto branco; hover em petróleo profundo.
- **Secundário (padrão):** papel com borda pedra forte e texto grafite; hover em areia funda.
- **Sutil:** sem fundo nem borda, texto petróleo; hover em tinta de petróleo.
- **Perigo:** papel, texto erro, borda de erro misturada à pedra; hover em erro claro.
- **Lançar (na barra):** areia sobre petróleo, texto petróleo noite; hover em branco.
- **Hover / Focus / Active:** transições de 160ms com `cubic-bezier(0.22, 1, 0.36, 1)`; ativo desce 1px; foco em anel de 2px `foco` com 2px de recuo; desabilitado a 55% de opacidade.
- **Pequeno / Ícone:** 2,25rem de altura e 0,8125rem de texto; botão de ícone quadrado (2,75rem ou 2,25rem).

### Chips (selos)
- **Style:** pílula, 0,8125rem, 600, sem borda. Neutro em areia funda + cinza; petróleo em tinta + petróleo profundo; verde em verde claro + verde.
- **Previsto:** coral claro com texto grafite e contorno tracejado de 1px em borda coral.

### Segmentados (abas e escolhas)
- Trilho em areia funda com borda pedra e 3px de recuo; itens transparentes em cinza, 0,875rem 600, 2,375rem de altura. O item ativo vira papel, texto petróleo profundo e a sombra mínima do segmento ativo. Usado nas abas Orçamento / Fluxo de caixa / Por pessoa e nas escolhas de tipo e periodicidade.

### Cards / Containers
- **Corner Style:** 12px.
- **Background:** papel sobre a areia do fundo.
- **Shadow Strategy:** nenhuma (ver A Regra do Papel Plano).
- **Border:** 1px em pedra.
- **Internal Padding:** 1,25rem; listas internas com itens de 3,5rem separados por linha de pedra.

### Inputs / Fields
- **Style:** fundo branco, borda 1px pedra forte, 8px, 2,75rem de altura, texto em 1rem; hover escurece a borda para cinza claro.
- **Focus:** borda em `foco` e halo de 3px `rgb(43 127 140 / 0.2)`.
- **Error / Disabled:** inválido ganha borda erro e mensagem em 0,8125rem com ícone Lucide circle-alert; desabilitado em areia funda com texto cinza.
- **Valor:** o campo de dinheiro tem o prefixo "R$" fixo à esquerda e número tabular em 600.
- **Rótulo:** acima do campo, 0,875rem 600 grafite; ajuda em 0,8125rem cinza.

### Navigation
- **Desktop:** links em areia a 80% sobre petróleo, 0,875rem 600; hover e ativo em branco; o ativo ganha um traço de 3px em areia na base, que cresce do centro (200ms).
- **Celular:** barra inferior em papel com borda de pedra, cinco itens de ícone 1,375rem + rótulo 0,75rem; ativo em petróleo.

### Avisos
- Faixa de 8px com ícone de 1,25rem e texto 0,875rem: informativo em tinta de petróleo / petróleo noite, erro em erro claro, sucesso em verde claro. O aviso rápido é uma pílula petróleo noite com texto areia, centralizada na base.

### Painel lateral
- `dialog` em papel, 30rem, entra da direita em 260ms; cabeçalho com título Fraunces 1,375rem e divisória pedra. No celular vira folha com cantos superiores de 12px.

### A planilha do ano (assinatura)
- Tabela em papel com borda pedra e cantos de 12px; cabeçalho dos meses em areia funda, rótulos em caixa alta 0,8125rem; mês atual em tinta de petróleo forte com texto petróleo profundo e coluna inteira em tinta de petróleo translúcida.
- Linhas de tipo (RECEITAS, DESPESAS) em areia, 700, separadas por 2px de pedra forte; grupos em 650 com seta que gira 90° ao abrir; categorias recuadas 1,5rem.
- Categoria com mais de um item ganha uma seta que abre as linhas dos itens, recuadas e em texto menor, abaixo dela.
- Célula prevista: coral claro com tracejado recuado. Total, Média, % ano e Rank numa faixa de indicadores depois dos meses: divisória dupla em pedra forte, fundo levemente tingido (`tinta-indicadores`, por cima do fundo da linha) e Média, % e Rank em 0,8125rem cinza; o Total segue em 700. Os nomes dos meses no cabeçalho em grafite, os indicadores em cinza.
- A copa do ano acima dos meses: barras de 1,75rem por 4,25rem; realizado em petróleo cheio, previsto empilhado por cima em coral claro tracejado, traço verde de 3px marcando a renda; ano vazio mostra barras fantasma tracejadas em pedra forte. Legenda com amostras dos três.

### Olho (esconder valores)
- Botão de ícone na barra, à esquerda do usuário: 2,75rem, olho Lucide em areia a 80%; ligado vira petróleo noite com o ícone de olho cortado e `aria-pressed`. Valores em reais viram "•••••" pelos formatadores (`reais`, `reaisCurtos`); campos de digitação não mudam.

### Faixa do mês (atalho do valor)
- Célula de categoria (ou item) × mês é um botão que ocupa a célula inteira; no hover, texto em petróleo profundo com sublinhado pedra forte. Abre uma faixa logo abaixo da linha, na largura da tabela, com título = categoria (Fraunces) e subtítulo = mês; o painel lateral não é usado aqui. Cada previsto ocupa uma linha (descrição, valor e ações), nunca cartões. Blocos "Previsto"/"Previsto para receber" e "Lançado"/"Recebido" com rótulo em caixa alta 0,8125rem; itens de 3,5rem com seta. Um lançamento só abre direto a edição; mês vazio abre o "Lançar".

### Conferir ocorrência
- O previsto num selo coral tracejado e três ações pequenas: primário (Confirmar / "Recebi R$ X"), secundário (Outro valor / "Veio outro valor") e sutil (Pular o mês / "Não recebi"). "Outro valor" troca as ações por um campo de valor com confirmar e voltar. Ações menos usadas (pular, vencimento do mês) ficam em "Mais". Se a data escolhida cai em outro mês, um aviso em texto pequeno diz em que mês o valor vai contar. O mesmo componente serve a lista de pendentes e a faixa do mês.

### Explicação de coluna
- Cabeçalho com ícone de info abre um `popover` nativo em papel (26rem, borda pedra, 12px, sombra de menu), título Fraunces 1,125rem, texto 0,875rem e um exemplo do próprio ano em areia. Usado no Rank.

### Estado vazio
- Centralizado, ilustração mínima de barras (tinta de petróleo forte para realizado, coral claro tracejado para previsto), título 1,125rem, texto de 46ch e ações centralizadas.

## Do's and Don'ts

### Do:
- **Do** usar o par previsto × realizado sempre do mesmo jeito: coral claro com tracejado de 1px em borda coral para previsto, petróleo cheio para realizado.
- **Do** usar algarismos tabulares em todo número (tabelas, campos de valor, resumos).
- **Do** manter superfícies em papel com borda de 1px em pedra e cantos de 12px; botões e campos com 8px.
- **Do** garantir alvo de toque de 2,75rem em botões e campos e anel de foco de 2px em `foco`.
- **Do** acompanhar todo erro de ícone e texto, nunca só cor.
- **Do** usar as transições de 160ms a 450ms com `cubic-bezier(0.22, 1, 0.36, 1)` e respeitar `prefers-reduced-motion`.
- **Do** usar ícones Lucide de linha, traço de 1,75px.

### Don't:
- **Don't** pintar despesa de vermelho; despesa é grafite.
- **Don't** usar coral como texto sobre fundo claro nem em mensagens de erro.
- **Don't** montar a tela como dashboard de cartões com número gigante; o número grande em Fraunces é só o da simulação.
- **Don't** usar Fraunces em rótulos, botões, tabelas ou textos, nem introduzir uma terceira família de fonte.
- **Don't** dar sombra a superfícies em repouso; sombra é só para menu, painel lateral, aviso rápido e barra inferior.
- **Don't** usar sombras pretas; elas são tingidas de petróleo noite ou grafite.
