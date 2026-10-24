# Objetivo

Um sistem aque me permita ter uma visão anual de despesas. Atualmente já realizo meu controle pessoal com uma planilha que me atende super bem, porém queria dar um passo maior nos níveis de controle e de posssibilidades.

O principal objetivo é ser um orcamento familiar, sendo assim, teremos grupos de pessoas alimentando a mesma conta o que dará uma visáo familiar muito boa.

Inicilamente será um produto que eu irei testar e utilizar para controle pessoal familiar mas a ideia, e sempre tenha isso em mente, é que ele vire um produto e que possamos cobrar assinatura para sua utilizacao.

O maior diferencial dele será a visão anual das depesas e o impacto de uma pequena despesa no ano. Eu sempre tive essa dificuldade em outras ferramentas porque eu cadatrava uma despesa mas sempre calculava mentalmente quando aquilo representava. Hoje com essa planilha eu consigo ter uma visão rápida de quanto cada caterogia e grupo de despesa representa no ano.

Alem disso o diferencial é ser uma ferramenta para familia. E nao limitado a ser uma planilha de gastos pessoais. No meu caso mesmo, eu sou o provedor da familia e toda a familia precisa ter clareza do impacto das depesas delas no orcamento do provedor.


Vamos dar o nome de Familiança ao sistema. Família + finança + aliança. São três palavras numa só: o “-liança” de família, o “-nança” de finança e a ideia de aliança, ou seja, a família unida em torno do orçamento. Combina exatamente com o que você descreveu: o provedor e a família alinhados, cada um ciente do impacto dos seus gastos.

Soa natural em português, como se a palavra já existisse.
Não passa a ideia de controle, e sim de pacto.
Uma frase possível: “Familiança: o orçamento é de todos.”
Domínios: familianca.com.br ✅, familianca.com ✅, familianca.app ✅, livres nas três extensões.

# Onde se inspirar
- Irei passar a planilha que eu uso atualmente para que sirva de base. projeto/Orçamento Familiar.xlsx
- Seguir a mesma formula do ../controle-alugueis
  - Mesma stack
  - Mesma estrutuda de ambiente de desenvolvimento com Dev Container
  - Mesma estrutura de containers
  - Mesma Arquitetura — Hexagonal (Ports & Adapters)
  - Vamos seguir com o deploy na mesma vps já utilizada no controle de alugueis
  - Mesma logica de seguranca de usuários

# regras de negocio
- Obter inicilamente as regras da planilha informada
- O sistema deve permitir a inclusao, remocao e desativaáo de cagetorias e grupos de categorias das despesas e receitas.
- Sempre que uma conta nova for criada, vamos decidir quais categorias básicas devem ser criadas. Já para facilitar a experiencia do usuário
- O sistema será multi usuário poderemos ter mais de um

# Rules
- Nunca commit a planilha que estou fornecendo como inpiracao. Ela tem dados pessoais. Só use para que possamos contruir a solução.
- usar o skill do REA se for necessário
- usar skill do impecable quando entramos na fase de frontend
- Esse arquivo será nosso principal documento de projeto, então tudo que for criado serja registrado aqui automaticamente. Isso nao inclui os documentos tecnicos ( deploy, detalhes de arquitetura, readme, ambiente de desenvolvimento, deploy em producao ,etc) que estarão em documentos separados. Aqui será o projeto em alto nível (regras de negocio, pendencias, planos futuros etc.). 


# Pontos de atenção
Pontos de atenção constante na hora de desenvolver.
- Futuramente teremos assinatura para utilizacao do servico em mar aberto. Entáo sempre que for criar uma estrutura nova pensar nisso, visto que geralmente em assinatura de servicos dessa forma, as funcionalidades são separadas por planos. Tenha sempre isso em foco desde o comeco do desenolvimento para já estarmos preparados para esse momento de comecar a cobrar e integrar com pagamento recorrente.
- Pensar que futuramente teremos um sistema mobile consumindo nosso backend.
- **Segurança em todo desenvolvimento.** Toda funcionalidade nova (backend, front, infra) é avaliada contra os 13
  requisitos mínimos (HTTPS, senhas com hash, MFA, rate limit, validação de inputs, sanitização, SQL injection,
  migrations, rollback, controle de acesso, expiração de sessão, secrets, CORS) e aplica os que forem pertinentes.
  O checklist prático está em [`docs/seguranca.md`](../docs/seguranca.md), seção "Checklist para cada
  desenvolvimento". O que ficar de fora entra no plano do mesmo arquivo, e nunca é esquecido.



---

# Andamento

| Fase | Situação |
|---|---|
| Backend — fase 1 (núcleo, visão anual, família, investimentos, PJ) | ✅ Implementado e testado (2026-10-17) |
| Coleção do Bruno + Swagger para testar tudo pela API | ✅ |
| Marca e identidade visual v1 ([`marca.md`](marca.md)) | ✅ Definida (2026-10-17); validar com famílias e criar o logo |
| Frontend v1 em Vue (skill impeccable) — todas as telas do backend | ✅ Implementado (2026-10-17); validar com a família |
| Carga da planilha real (2022–2026) pela API, na família do provedor (ambiente local) | ✅ Feita (2026-10-17); totais conferem com a planilha |
| Deploy no VPS do controle-alugueis | ⏳ Infra pronta (`infra/deploy`), guia e subida pendentes |
| Assinatura paga (gateway de pagamento recorrente) | 📋 Futuro |
| App mobile | 📋 Futuro (backend já preparado) |

# Decisões tomadas

- **Lançamentos + recorrência** (não uma grade valor × mês como a planilha): cada receita/despesa é um lançamento
  com data, valor, categoria e a pessoa a quem pertence. O que se repete é uma **recorrência**, que aparece como
  **prevista** no ano inteiro até ser confirmada. Assim a visão anual fica completa desde janeiro, como na planilha,
  e ainda sabemos quem gastou o quê.
- **Família = conta.** Cadastro público: quem se cadastra cria a família e vira **DONO**; os demais entram por
  **convite** (link). Por enquanto cada pessoa participa de uma única família.
- **Papéis na família:** DONO (gerencia membros, convites, categorias, saldos, investimentos e assinatura; altera
  qualquer lançamento) e MEMBRO (lança e altera só os próprios e vê o orçamento inteiro da família).
- **Fase 1 inclui** inflação familiar, saldo/fluxo de caixa, investimentos e despesas PJ, além do núcleo.
- **Front em Vue 3** (2026-10-17), servido pelo próprio Spring Boot (mesma origem). A tela principal é a **visão
  anual em formato de planilha** ("planilha viva"): grupos que abrem em categorias, 12 meses, total, média, % e rank,
  com 12 barras do ano no cabeçalho (despesa confirmada cheia, prevista tracejada em coral, traço verde da renda). No
  celular, as 12 barras viram o seletor de mês.
- **Nomes dos papéis na tela:** DONO aparece como "organiza" e MEMBRO como "participa" (a marca evita "dono" e
  "chefe da casa").
- **Planos desde o início** (ver "Planos e assinatura"): toda funcionalidade nova que puder ser vendida separada
  entra como um recurso de plano.

# Regras de negócio

## Família, pessoas e acesso
- Cadastro: nome, e-mail (login, único na plataforma) e senha de 12 a 64 caracteres. A família nasce com o nome
  informado ou "Família de <primeiro nome>", com as categorias padrão e no **plano completo em teste por 30 dias**.
- Convite: o DONO informa o e-mail e o papel; o sistema gera um link de uso único, válido por 7 dias, para enviar
  por WhatsApp/e-mail. Quem abre o link cria a conta (nome e senha) com o e-mail do convite e já entra na família.
  Não dá para convidar quem já tem conta ou já tem convite pendente; convite expirado pode ser refeito.
- Pessoas na família = membros ativos + convites pendentes, limitadas pelo plano.
- Remover um membro o **inativa**: ele não entra mais, mas os lançamentos dele continuam no histórico da família.
- Ninguém remove nem muda o próprio papel, e a família sempre tem pelo menos um DONO ativo.
- Mudar o papel ou remover alguém encerra as sessões abertas dessa pessoa.
- Todos da família veem tudo da família; nenhuma família vê nada de outra.

## Categorias (plano de contas)
- Estrutura da planilha: **tipo** (receita/despesa) → **grupo** com natureza **fixa** ou **variável** (ex.:
  Habitação, Saúde, Transporte, Despesas temporárias) → **categoria** (ex.: Supermercado, Plano de saúde).
- Categorias básicas criadas em toda família nova (baseadas na planilha, sem dados pessoais):
  - Receitas fixas: Salário, Benefícios, Aluguéis recebidos, Pensão e auxílios, Outras.
  - Receitas variáveis: 13º, Férias, Bônus e PLR, Trabalhos extras, Dividendos e rendimentos, Reembolsos, Outras.
  - Despesas fixas: Habitação, Saúde, Transporte, Despesas pessoais, Educação, Lazer, Outras despesas fixas.
  - Despesas temporárias e variáveis: manutenção da casa e do veículo, médicos esporádicos, viagens, presentes,
    utilidades domésticas, material de escritório, correios, outros.
- O DONO inclui, edita, move (dentro do mesmo tipo), **desativa** e **exclui** grupos e categorias. Desativar
  impede novos lançamentos e preserva o histórico; excluir só vale sem uso (grupo sem categorias; categoria sem
  lançamentos e recorrências). Nomes não se repetem no mesmo tipo (grupo) ou grupo (categoria).
- **Categoria recorrente (2026-10-17):** a categoria pode ser marcada como "repete todo mês" (ex.: aluguel,
  telefone, salário). Ao lançar nela, o "Repetir no ano" já vem marcado: o lançamento vira uma recorrência a partir do
  mês da data, esse mês entra confirmado (se a data já passou) e os próximos aparecem como previstos. Dá para
  desmarcar e lançar só uma vez. As categorias padrão de família nova já vêm marcadas (salário, benefícios,
  aluguéis, aluguel/financiamento, condomínio, energia, água, gás, internet, telefone, serviços domésticos, planos de
  saúde e odontológico, financiamento do veículo, academia, mesadas, escola, pós, tarifas, assinaturas, serviços
  digitais; no modelo PJ, INSS, contador e pró-labore). Famílias que já existiam começam com tudo desmarcado.
  Categoria calculada por alíquota não pode ser recorrente.
- **Tela de categorias (2026-10-17):** abas Despesas/Receitas, busca por nome, "Mostrar desativadas", os grupos à
  esquerda e as categorias do grupo escolhido à direita (no celular, o grupo abre numa tela própria).
- **Categoria no lançamento (2026-10-17):** o campo de categoria (lançamento e recorrência) tem busca pelo nome da
  categoria ou do grupo, sem diferenciar acento. Sem resultado igual, o DONO cria a categoria ali mesmo, escolhendo o
  grupo, e ela já fica escolhida; se existe uma desativada com o mesmo nome, ele a reativa em vez de duplicar. O
  MEMBRO vê que deve pedir a quem organiza.
- **Selos da categoria (2026-10-18):** ao lado do nome, na visão anual (computador e celular) e em Categorias,
  selos de uma letra mostram o jeito da categoria: **R** recorrente (repete todo mês), **T** tributável, **C**
  calculada por alíquota e **E** estimativa própria (regra diferente da família; só em despesa que não se repete).
  Passar o mouse mostra a explicação completa; no celular, tocar no selo mostra a explicação num aviso. Sem selo =
  categoria comum (não recorrente). Em Categorias há a legenda dos selos.
- Item "Cartão de crédito X" da planilha não virou categoria: no sistema o gasto é lançado na categoria do que foi
  comprado (ver pendência sobre cartões).

## Lançamentos
- Contam no mês da data. Valor sempre positivo; o tipo vem do grupo da categoria.
- Cada lançamento pertence a uma pessoa da família (padrão: quem lança). MEMBRO só lança em nome próprio.
- **Compra parcelada:** valor total dividido em 2 a 60 parcelas mensais a partir da data da 1ª; os centavos da
  divisão vão para a 1ª parcela. Dá para excluir uma parcela ou a compra inteira.
- Categoria desativada ou calculada por alíquota não recebe lançamento.
- **Descrição sugerida (2026-10-18):** ao lançar (e ao cadastrar recorrência), a categoria vem antes da descrição.
  Escolhida a categoria, o campo vazio mostra as **3 descrições mais comuns** dela como sugestão direta. Ao
  digitar (a partir de 2 letras), o sistema **busca** em todos os lançamentos e recorrências da categoria as
  descrições que contêm parte do termo ou se parecem com ele, sem ligar para acento, maiúscula ou erro de
  digitação ("acogue" acha "Açougue", "merc" acha "Mercadinho do bairro" e "Supermercado atacadão"); as que começam
  com o termo vêm primeiro, até 8, com o trecho encontrado em destaque. Escrever uma existente de outro jeito mostra
  "Já existe X" com um botão para usar a já escrita. Objetivo: a família chamar a mesma coisa pelo mesmo nome, mesmo
  com o histórico grande.
- **Repetir com vencimento (2026-10-22):** ao lançar uma despesa marcando "Repetir", dá para informar o dia do
  vencimento (opcional). Em branco, a recorrência fica sem vencimento fixo.
- **Filtros da lista (2026-10-23):** período por **mês** (com setas), **ano** ou **entre datas**; **busca** por parte da
  descrição, observação, categoria, grupo ou pessoa (sem ligar para acento e maiúscula, até 200 letras); **categoria**
  (só as do tipo escolhido); **origem** (lançados uma vez, confirmações do que se repete ou parcelas de compras);
  **tipo** e **pessoa**. Os totais somam só o que aparece. Os filtros ficam no endereço da página (dá para voltar,
  recarregar e guardar o link); "Limpar filtros" tira tudo menos o período. Vindo da tabela do ano com uma
  categoria, abre o ano inteiro dela.
- **Data do pagamento na lista (2026-10-22):** em Lançamentos, cada item mostra "pago em" (despesa), "recebido em"
  (receita) ou "entrou na conta em" (nota fiscal) com a data.

## Recorrências
- Receita ou despesa que se repete: mensal, bimestral, trimestral, semestral ou anual, a partir de um mês de
  início, até um mês de fim (opcional), num dia do mês (31 vira o último dia em meses curtos).
- Cada ocorrência aparece como **prevista** na visão anual até ser **confirmada** (vira lançamento com o valor e a
  data reais) ou **pulada** (não houve naquele mês). Excluir a confirmação volta a ocorrência para prevista.
- **Confirma sozinha** (por recorrência, só despesa, 2026-10-17): para débito automático e conta de valor fixo. O
  que vence a partir do dia em que a opção foi ligada vira lançamento sozinho, com o valor e a data previstos (o que
  já tinha vencido continua para conferir). Valor diferente: edita-se o lançamento. Excluir essa confirmação marca o
  mês como pulado ("não houve"), senão ela voltaria a ser confirmada. Receita nunca confirma sozinha. Conta que varia
  (energia, água) fica desligada e segue pedindo conferência. A confirmação roda nas consultas da própria família
  (visão anual, lançamentos, recorrências), sem tarefa agendada; categoria desativada ou pessoa inativa ficam nas
  pendentes.
- **Receita recorrente pode não acontecer** (2026-10-17): a conferência de uma receita pergunta "chegou?" (Recebi o
  valor previsto / Veio outro valor / Não recebi) e a renda prevista aparece à parte no resumo do ano. Despesa
  recorrente segue com "confirmar o valor" ou "pular o mês". Na lista de pendentes, receitas e contas ficam separadas.
- **Pular o mês pede confirmação (2026-10-17)** e explica o que acontece: o valor sai do ano, nada é lançado e os
  outros meses continuam. Para desfazer, "Voltar a prever" aparece no próprio mês (clicando na célula da visão
  anual) e no topo da recorrência, em Recorrências, que mostra na lista o mês pulado.
- **Confirmação só muda valor e data (2026-10-24):** editar o lançamento que confirmou uma recorrência (pela tabela
  do ano ou em Lançamentos) mostra só valor, data, observação e nota; a categoria, a descrição e de quem é vêm da
  recorrência, com o link "editem a recorrência" (vale para todos os meses). O servidor recusa a mudança (422). Antes
  dava para mover a confirmação de junho do Salário para outra categoria: o mês contava lá, mas seguia "confirmado"
  na recorrência e entrava na previsão dela. Lançamento avulso continua com tudo editável (corrigir a categoria
  errada é o caso comum). Confirmações antigas que já estavam noutra categoria seguem editáveis no valor e na data.
- "Pendentes": ocorrências dos últimos 12 meses que já venceram e ainda não foram confirmadas nem puladas.
- Editar a recorrência muda as ocorrências não confirmadas. Com ocorrências confirmadas não dá para excluir:
  define-se o fim.
- Cada recorrência mostra quanto pesa no ano atual e num ano cheio (ex.: R$ 39,90/mês = R$ 478,80/ano).
- **Vencimento das contas (2026-10-18)** — base da lista de contas a pagar (o "azul pago / vermelho
  pendente" da planilha). Cada regra tem texto explicativo na própria tela.
  - Na despesa, o dia da recorrência é o **vencimento** ("Vence no dia"); na receita, o dia em que costuma cair
    ("Cai no dia"). As recorrências que já existiam passam a usar o dia que tinham como vencimento.
  - **"Não tem vencimento fixo"** (opcional, não obriga): para conta sem data certa (mesada, feira do mês). O dia só
    posiciona a conta no mês e o pagamento não aparece como em dia ou com atraso.
  - **Como é paga** (opcional, só despesa): **boleto ou Pix** (alguém paga até o vencimento: é o que vai para a
    lista de contas a pagar), **débito automático** (sai sozinho; só conferir o valor — e, se for fixo, ligar
    "confirmar sozinha") ou **no cartão de crédito** (entra na fatura, então não tem vencimento próprio: quem vence é
    a fatura). Receita não tem forma de pagamento.
  - **Vencimento diferente só num mês:** o boleto do mês veio para outro dia → muda só aquela ocorrência (até 2 meses
    antes ou depois), com "voltar ao dia X". Esse vencimento passa a ser a data da ocorrência: decide quando ela
    vence (e entra em "para conferir") e é a data sugerida ao confirmar. Conta sem vencimento ou no cartão não tem.
  - **Previsto diferente só num mês (2026-10-23):** a conta do mês veio mais cara ou mais barata, mas ainda não foi
    paga → muda só o valor previsto daquela ocorrência, **sem confirmar nada** ("Outro valor" → "Só mudar o
    previsto"), com "voltar ao previsto de sempre". Vale acima da estimativa pela regra, entra nos totais do ano e é o
    valor usado ao confirmar sem informar outro (inclusive na confirmação automática). Já confirmada, muda-se o
    lançamento. Vale para receita e despesa.
    - **Só neste mês ou daqui em diante (2026-10-23):** ao escolher "Só mudar o previsto", a tela pergunta o alcance
      (não pergunta em conta avulsa nem no último mês). **Daqui em diante** é um reajuste: o valor da recorrência
      passa a ser o novo a partir daquele mês; os meses antes que ainda estão previstos ficam com o previsto que
      tinham (guardado só neles) e os confirmados não mudam. A estimativa recomeça a contar desse mês, e os ajustes de
      um mês só daí em diante saem.
  - O mês confirmado com data de outro mês (pagamento adiantado ou atrasado) conta no mês da data e deixa de
    aparecer como previsto no mês da ocorrência, inclusive na faixa do mês da visão anual.
  - **Pagar = confirmar.** Ao confirmar uma conta com vencimento, informa-se "Pago em" (ou "Debitado em"): já vencida,
    vem preenchido com o vencimento (supõe pago em dia; corrige-se se foi depois); ainda a vencer, com hoje. Num mês
    futuro (pagamento adiantado), vem com o vencimento, para o valor não ir para o mês atual; se a data escolhida cair
    em outro mês, a tela avisa em que mês o valor vai contar (2026-10-23). Comparando
    com o vencimento, o lançamento fica **pago em dia** ou **com N dias de atraso** (selo nos Lançamentos e no mês da
    visão anual).
  - **Conta avulsa (uma vez só):** nova frequência "Uma vez só" para conta que não se repete (IPTU em cota única,
    boleto de conserto): prevista só naquele mês até ser paga. Também dá para criar ao lançar uma despesa, marcando
    **"Ainda não paguei (conta a pagar)"**: a data vira o vencimento e a despesa fica prevista, sem contar como paga.

- **Contas a pagar — checklist do mês (2026-10-24):** tela "Contas a pagar" (menu, entre O ano e Lançamentos),
  como a planilha em que se pinta de azul o que foi pago. Abre no mês atual, com setas (meses futuros para pagar
  adiantado, até o fim da previsão) e o mês no endereço.
  - **O que entra:** despesas que se repetem ou avulsas que ocorrem no mês (puladas ficam de fora). **A pagar:**
    boleto/Pix e as que **não dizem como são pagas** mas têm vencimento (a maioria das contas antigas; mostram "É débito
    automático? Diga como é paga", que abre a edição ali mesmo). **Só para conferir:** débito automático e cartão,
    numa seção à parte; o cartão aparece conta a conta, porque a fatura ainda não existe. Sem forma e sem vencimento
    (feira, mesada) e receitas não entram.
  - **Agrupadas** em vencidas, vencem em até 7 dias e depois; as pagas ficam num grupo fechado, com o total.
  - **Pagar = confirmar:** "Paguei" **sempre abre a conferência** (valor e data à vista antes de gravar), o mesmo
    componente da visão anual. A paga ganha o selo "paga" e "em dia" / "N dias de atraso", e dá para desfazer (exclui
    o lançamento e a conta volta a ser prevista).
  - **Mês concluído é calculado, nada gravado:** "Tudo pago em <mês>" quando não sobra conta a pagar em aberto;
    entrando uma conta nova, o mês reabre sozinho. Enquanto isso, "Faltam R$ X · N de M pagas". A seção "só para
    conferir" não impede a conclusão (sai sozinha) e mostra só "N a conferir".
  - **Meses anteriores:** só no mês atual, um aviso "N contas de <mês> em aberto → ver <mês>" (vencidas e não pagas
    dos últimos 12 meses). Não impede o mês atual de ficar concluído.
  - **Quem vê e quem paga:** toda a família vê (quem participa já vê o orçamento inteiro). Marcar como paga segue a
    regra de sempre: quem organiza marca qualquer conta; quem participa, só as que estão em seu nome (nas outras não
    aparece "Paguei"). **Muda a decisão de 2026-10-18** ("só quem organiza vê, configurável"): esconder não protegia
    nenhum dado, e a configuração seria tela e regra a mais sem restringir nada.

## Visão anual (o diferencial)
- **Previsão até 2 anos à frente (2026-10-24):** os previstos (recorrências e estimativas) alcançam só os dois anos
  depois do atual (em 2026: 2027 e 2028). Mais adiante não há visão do ano (a seta para o próximo ano para no
  último; a API responde 422 "A previsão vai até 2028") e as ocorrências dessas competências não se confirmam, pulam
  nem ajustam (valor ou vencimento). Projetar tão longe não ajuda a família e custa processamento: cada ano é
  calculado encadeando os anteriores, e um ajuste num mês distante seria dado guardado que ninguém vê. O horizonte
  anda com o calendário (em 2027, vai até 2029). A recorrência continua podendo ter fim depois disso (ou nenhum).
- **Até onde voltar no passado (2026-10-24):** as setas param no **primeiro ano com algum dado** da família (o mais
  antigo entre lançamentos, início de recorrências, saldos informados e movimentos de investimento); família nova
  começa no ano atual. Antes a visão anual voltava até 2000, ano a ano vazio. O limite anda sozinho: lançar algo de
  2019 traz 2019. Vale nas setas do ano (visão anual, investimentos, lançamentos por ano) e do mês (lançamentos e
  contas a pagar param em janeiro desse ano); o filtro "entre datas" dos lançamentos continua livre. Só a visão anual
  é recusada pelo servidor antes desse ano (422 "Não há dados antes de 2022"); um endereço mais antigo abre o primeiro
  ano. Não foi usado o ano de criação da família: a carga da planilha traz anos antes dele.
- **Atalho do valor (2026-10-17):** clicar no valor de uma categoria num mês abre o que forma aquele valor:
  lançamentos (editar, excluir ou desfazer a confirmação) e previstos (confirmar ou pular). Com um lançamento só,
  abre direto a edição; com o mês vazio, abre o "Lançar" com a categoria e a data preenchidas.
  - **Edição na própria linha (2026-10-22):** o que forma o valor abre numa faixa logo abaixo da linha clicada, sem
    painel lateral; um item por linha (não cartões), com confirmar, outro valor e "Mais" (pular, vencimento do mês).
- **Filtro rápido pelo nome (2026-10-24):** na aba Orçamento (tabela do computador), "Buscar despesa ou receita"
  mostra só as categorias e os itens cujo nome tem o texto (sem ligar para acento e maiúscula), dentro dos grupos.
  Achada só por um item, a categoria abre e mostra só ele. Enquanto filtra, os totais de bloco e de grupo saem (seriam
  do ano inteiro e enganariam) e entra **"Receitas encontradas" / "Despesas encontradas"**, separadas, com os 12 meses
  e o total: conta a categoria inteira quando o nome dela bate, senão só os itens que batem (nada é somado duas vezes).
  As linhas de inflação e de receitas − despesas somem; o resumo do topo e as barras continuam sendo do ano. Acha
  categoria sem valor no ano. O texto continua ao trocar de ano, não fica guardado (sair da tela ou recarregar volta
  tudo) e Esc limpa. Tudo no navegador, sem consulta nova. Fora por enquanto: o celular (outro componente, um mês por
  vez) e as abas Fluxo de caixa e Por pessoa.
- **Categoria em itens (2026-10-22):** a seta ao lado da categoria abre as linhas dos itens que a compõem (ex.: três
  telefones em "Telefone"): uma linha por recorrência, uma por descrição dos lançamentos avulsos (sem ligar para
  acento e maiúscula) e uma para a estimativa. A célula do item abre só o que é dele. Quais categorias ficam abertas
  é guardado só no navegador de cada pessoa.
- **Rank:** posição da categoria pelo total do ano, 1º = a que mais pesa, contada à parte para receitas e
  despesas. Mostrado como "1º", com explicação e exemplo do próprio ano no cabeçalho da coluna.
- **Esconder valores (olho na barra):** troca todo valor em reais por "•••••" em todas as telas, para usar em
  público ou mostrar a tela sem expor números. Fica guardado só no navegador de cada pessoa; percentuais, rank e o
  formato das barras continuam visíveis.
- **Centavos (2026-10-17):** as tabelas do ano (visão anual, fluxo de caixa, por pessoa, carteira) mostram o valor
  arredondado para o real por padrão; "Mostrar centavos" (na tabela ou em Minha conta) mostra o valor exato. Como o
  olho, fica guardado só no navegador de cada pessoa.
- **Estimativa dos próximos meses (2026-10-17):** despesa que não se repete (não recorrente, não calculada) ganha,
  nos meses depois do atual, uma estimativa a partir dos meses que já passaram no ano: **média**, **maior mês**,
  **menor mês** ou **não estimar**. A família escolhe a regra padrão (começa pela média, inclusive nas famílias que
  já existiam) e cada categoria pode seguir a família ou ter a sua (em Categorias). Regras:
  - Conta a partir do primeiro mês do ano com algum lançamento da família (antes disso ela não usava o app); a
    média inclui os meses zerados dessa janela.
  - O mês atual não é estimado (está em andamento); anos passados e futuros também não.
  - Cada mês recebe só o que falta para chegar na estimativa: o que já foi lançado (parcelas) ou está previsto por
    recorrência é descontado. Lançou o gasto real, ele ocupa o lugar da estimativa.
  - A estimativa aparece como previsto (coral tracejado), entra nos totais, no fluxo de caixa e na inflação, e fica
    fora da visão por pessoa (não é de ninguém). Receitas não são estimadas.
  - Clicar no mês estimado explica o valor e de onde veio a regra.
  - **Contas que se repetem com valor variável** (DAS, energia, 2026-10-17): o previsto de cada ocorrência do mês
    atual em diante segue a mesma regra, aplicada às confirmações daquela recorrência no ano (média, maior ou menor
    valor confirmado), em vez do valor cadastrado. Sem confirmação no ano, vale o cadastrado. Ficam de fora receitas
    e contas que confirmam sozinhas (valor fixo). Confirmar sem informar o valor usa esse previsto. Mudar o valor
    cadastrado (reajuste) zera a base: só as confirmações a partir daquele mês contam.
  - **Média dos últimos 3 meses e regra por conta (2026-10-23):**
    - Nova regra **"Média dos últimos 3 meses"**: na despesa avulsa, a média dos 3 meses que já passaram (sem o mês
      em andamento); na conta que se repete, das 3 últimas confirmações (de competência mais recente). Cruza o ano (em janeiro usa outubro a
      dezembro), então funciona desde o começo do ano e acompanha reajustes. Só contam meses a partir de quando a
      família começou a lançar. Média do ano, maior e menor mês continuam olhando só o ano corrente.
    - Virou o **padrão da família**: as famílias que seguiam a média passaram a ela (inclusive as que já existiam);
      categorias com regra própria não mudaram.
    - **Quem manda:** a regra da própria conta que se repete (nova), senão a da categoria, senão a da família. O
      previsto ajustado à mão num mês ("Só mudar o previsto") vale acima de tudo.
    - **Fica claro:** na faixa do mês, cada conta prevista e a estimativa da categoria mostram a regra, de quem ela é
      e os meses usados ("Média dos últimos 3 meses (padrão da família): ago R$ 410 · set R$ 380 · out R$ 395 →
      R$ 395 por mês"). A regra da conta muda quem cadastrou ou quem organiza; a da categoria, só quem organiza.
      Receita e conta que confirma sozinha não têm regra.
  - **Regra no nome da despesa e a partir de um mês (2026-10-24):**
    - **Onde muda:** a regra saiu da faixa do mês (clicar no valor é para confirmar, pular, lançar). Na visão anual,
      clicar no **nome da despesa** (a conta que se repete ou a linha da estimativa) abre logo abaixo a configuração;
      em **Categorias**, o texto "previsão: …" sob o nome da categoria abre a da categoria (agora também nas
      categorias que se repetem, porque a regra delas vale para as contas da categoria).
    - **Primeiro a configuração atual:** ao abrir, mostra a regra que vale agora, de quem é, os meses usados, o que
      muda adiante ("A partir de jan/27: …") e os meses ajustados à mão. Na escolha, a opção atual vem marcada.
    - **Menos informação (2026-10-24):** na linha da categoria fica só a regra ("média 3 meses"); "como a
      família" e as contas com regra própria vão para a dica. No item, a regra só aparece quando foge da categoria
      (regra própria, mudança marcada ou sem histórico). O texto ficou menor e mais claro que o nome.
    - **Sempre à vista, sem chamar atenção:** sempre na linha de baixo do nome da despesa (na categoria fechada com um item só, depois dos selos),
      um texto pequeno e cinza diz a regra configurada: "média 3 meses", "maior mês", "valor cadastrado"
      (não estimar), "menor mês · sem histórico" (a conta ainda não tem mês confirmado para calcular) e, se a regra muda depois, "→ maior mês em jan/27". O detalhe fica na
      dica ao passar o mouse.
    - **Alcance:** sem mês, a regra vale para **todos os meses previstos**, deste ano e dos próximos, e **não
      sobrescreve** os valores ajustados à mão. Com um mês (o atual ou um dos próximos 17), vale **daquele mês em
      diante**: os meses antes continuam com a regra de hoje e os ajustados à mão daquele mês em diante deixam de
      valer (na categoria, também os das contas que a seguem). A tela diz antes de aplicar o que vai acontecer.
    - Fica guardado na categoria e na conta: a regra, o mês em que começa e a regra de antes. Só a última mudança
      guarda o "antes". Trocar a regra pela edição da categoria (API antiga) vale para todos os meses.
    - **Toda confirmação entra na base (2026-10-24):** na conta que se repete, a regra usa todas as confirmações,
      inclusive a do mês atual e as pagas adiantado (confirmou dezembro em outubro, dezembro já conta). Conta
      confirmada é valor real e fechado; a regra de não usar o mês em andamento vale só para a estimativa das
      despesas avulsas. Reajuste do valor cadastrado com o mês atual já confirmado passa a valer do mês seguinte
      (a confirmação feita com o valor antigo não volta a contar). Sem nenhuma confirmação, toda regra cai no valor
      cadastrado e a tela diz isso ("… · sem histórico").
    - **Despesa nova nasce com a média dos últimos 3 meses (2026-10-24):** toda conta de despesa criada (inclusive
      pelo "Repetir" do lançamento e a conta a pagar) já vem com a regra própria "Média dos últimos 3 meses", e não
      "segue a categoria". Dá para trocar depois no nome da despesa. Fica de fora a conta que confirma sozinha (valor
      fixo) e a receita. As contas que já existiam não mudam.
    - **Regra da categoria vale para todas as contas dela (2026-10-24):** mudar a regra da categoria **substitui a
      regra própria** de cada conta dela (passam a seguir a categoria) e recalcula todos os previstos; com "a partir
      de", só desse mês em diante (antes, cada conta fica como estava). A conta que confirma sozinha fica de fora.
      Depois, cada conta ainda pode ganhar uma regra própria pelo nome dela. Onde muda: na visão anual, o texto sob
      o nome da categoria ("menor mês", "média 3 meses (como a família)", "· 1 conta com regra própria"); em
      Categorias, o "previsão: …" sob o nome. O painel lista as contas da categoria e a regra de cada uma, e avisa
      quais regras próprias serão substituídas.
    - **Histórico antes do início (2026-10-24):** a conta começou depois (Vivo - Moto em outubro), mas a família
      quer registrar meses anteriores. Na linha da conta, o mês antes do início (já chegado) oferece "Lançar
      <mês>": vira lançamento **ligado à conta** (mesma linha, conta no mês e entra na base da previsão) sem mudar
      a agenda. Só meses que já chegaram e não em conta avulsa. Antes, o "Lançar" abria o formulário comum, sem a
      descrição, e o lançamento ficava solto, numa linha à parte e fora da previsão. O "Lançar" de um item avulso
      agora já vem com a descrição dele.
  - **Nada é gravado:** os previstos são calculados a cada consulta. Mudar a regra (da família, da categoria ou da conta),
    editar um lançamento ou confirmar um mês recalcula tudo na hora. A tela pede confirmação antes de trocar a
    regra da família e explica o que vai ser recalculado.
- O ano mês a mês, com total e média, para cada categoria, grupo, receitas e despesas. Cada valor separa
  **realizado** (lançamentos) e **previsto** (recorrências).
- Para cada categoria e grupo: **participação** no total de receitas/despesas do ano e **ranking** das categorias
  (a coluna RANK da planilha).
- **Inflação** de cada grupo e **inflação familiar** (despesas totais): variação mês a mês e acumulada no ano,
  composta como na planilha. Diferença: mês zerado não entra como −100%, fica sem variação.
  - **Só mês fechado (2026-10-24):** no ano corrente, a variação vai só até o mês anterior ao atual. O mês em
    andamento (gasto ainda incompleto: dia 24 dava −18,5%, no começo do mês −90%) e os previstos ficam "–", com a
    explicação no mouse, e o acumulado vira **"até set"** (no lugar de "no ano"): antes ele equivalia a comparar o
    previsto de dezembro com fevereiro (−4,4% em vez dos −2,8% reais). Anos passados como sempre; ano futuro não tem
    linha de inflação. No celular, o mês em andamento mostra "mês em andamento". Vale para a família e cada grupo.
  - **Na primeira tela (2026-10-18), como na planilha:** a tabela do ano mostra a linha "Inflação da família" logo
    abaixo de Despesas e uma linha "Inflação" sob cada grupo de despesa aberto, com a acumulada do ano na coluna
    Total (alta em coral, queda em verde, itálico quando a variação usa valor previsto). "Mostrar inflação" liga e
    desliga as linhas (vem ligado; guardado só no navegador). No celular, o resumo do mês mostra a inflação do mês e
    a do ano, e cada grupo de despesa a do mês.
- **Fluxo de caixa** no formato da planilha: (1) sobra do mês anterior, (2) renda total, (3) aplicações,
  (4) saldo disponível = 1 + 2 − 3, (5) despesas, fluxo líquido = 4 − 5.
  - A sobra do mês é o **saldo em conta informado** pela família no início do mês; sem ele, o fluxo líquido do mês
    anterior (o saldo vai sendo carregado).
  - **Dois saldos: o do dia 1º e o de hoje (2026-10-18, revisto no mesmo dia).** No fluxo vale sempre o saldo do dia
    1º. Quem começa no meio do mês costuma ter em mãos o saldo de hoje, que já descontou as contas pagas no mês;
    informar esse valor como o do dia 1º e lançar as contas pagas desconta tudo duas vezes. Por isso o painel tem
    **dois campos separados**, o do dia 1º primeiro e o de hoje (ou outro dia do mês até hoje) depois, e um nunca
    sobrescreve o outro. A família informa os dois ou só um:
    - **só o do dia 1º:** vale como está;
    - **o do dia 1º e o de hoje:** o do dia 1º continua sendo o ponto de partida, como foi digitado. O de hoje é
      guardado à parte, **só para conferir** (não muda o fluxo): a tela mostra dia 1º + receitas lançadas − despesas
      lançadas de 1º até o dia = esperado no dia, ao lado do saldo informado, e a diferença ("o banco tem R$ X a
      menos que o lançado: falta lançar alguma despesa", ou a mais: falta lançar alguma receita). Ex.: 10 no dia 1º,
      nada lançado e 5 hoje = faltam R$ 5 em despesas; o fluxo segue partindo de 10. Guardar sem o de hoje apaga a
      conferência. O saldo de hoje é só o total (sem lugar a lugar);
    - **só o de hoje:** o sistema guarda o valor e o dia, e o fluxo usa como dia 1º: saldo do dia − receitas lançadas
      de 1º até o dia + despesas lançadas de 1º até o dia (inclusive). Ex.: 100.000 em 18/10, com 3.000 de contas e
      9.000 de salário lançados em outubro = 94.000 no dia 1º. A conta é refeita a cada visão: conta paga no período
      e lançada depois entra sozinha; o que for lançado para depois do dia não entra;
    - **o saldo de outro dia só existe no mês corrente.** Num mês que já passou a família informa só o do dia 1º:
      ele está no extrato (o saldo do fim do mês anterior), e conferir o fim daquele mês é o mesmo que corrigir o
      saldo do mês seguinte. Mês que ainda não começou também só tem o do dia 1º. Exceção: o saldo guardado com dia
      quando o mês era o corrente continua valendo depois que o mês vira; aparece com o dia travado e pode ser
      corrigido mantendo o dia, ou apagado. O servidor recusa um dia novo num mês que já passou (422);
    - a conta aparece antes de guardar, em ordem de data, marcando o que a família informou e o que foi calculado;
    - aplicação em investimento não tem dia, então não entra na conta: só com o saldo de hoje, a tela pede para
      somá-la a ele.
  - **Saldo inicial e correções (2026-10-18).** A família informa o saldo **uma vez**, no mês em que começa a usar
    (o "saldo inicial"); nos meses seguintes cada mês começa sozinho com a sobra do anterior. Informar o saldo de um
    mês depois do inicial é uma **correção**: o painel mostra o valor calculado pelo sistema (a sobra do mês anterior)
    e a diferença para o valor real, que passa a valer daquele mês em diante. Na tabela, o botão "Corrigir o saldo de
    <mês atual>" (ou "Informar o saldo inicial") abre o painel direto.
  - **O saldo inicial pode ser de qualquer mês que já chegou (2026-10-18).** Enquanto a família não tem saldo
    nenhum, o painel mostra "Mês em que vocês começam" (vem no mês atual): vale este mês ou qualquer um que já passou,
    até de outro ano (a visão vai para aquele ano com o painel aberto). Mês futuro não serve: o servidor recusa o
    saldo inicial de um mês que ainda não chegou (422). Depois do saldo inicial, corrigir um mês adiante continua
    permitido.
  - **A sobra passa de um ano para o outro.** A partir do ano do primeiro saldo informado, janeiro começa com o fluxo
    líquido de dezembro do ano anterior (se janeiro não tiver saldo informado por cima). Antes do primeiro saldo,
    janeiro começa do zero.
  - **Saldo lugar a lugar.** O saldo pode ser informado só no total (o jeito rápido de corrigir) ou **lugar a lugar**,
    o padrão do saldo inicial. Os lugares são cadastrados uma vez e voltam prontos: conta corrente ou digital,
    poupança, dinheiro na mão, cofrinho, investimento de resgate rápido ou outro. Cada um tem dono opcional (um membro;
    sem dono = da família toda ou de alguém sem acesso, como o cofrinho de uma criança, com o nome da pessoa no nome
    do lugar). Com lugares, o total do mês é a soma deles; lugar em branco não entra naquele mês.
    - Retroativo a qualquer tempo: quem entra na família depois inclui os lugares dele no saldo inicial, com o saldo
      do mesmo dia, e o saldo inicial é refeito.
    - Investimento só entra como lugar se **não** for acompanhado na aba Investimentos; senão contaria duas vezes (a
      tela avisa).
    - Lugar que já entrou num saldo não pode ser excluído, só desativado: some dos meses novos e continua nos antigos.
      Em Família, "Onde está o dinheiro" lista os lugares para renomear, trocar o dono, desativar ou excluir. O
      próprio painel do saldo também edita e exclui o lugar (para corrigir um nome errado sem sair dali), sem perder
      os valores já digitados.
    - Sem a lista de lugares carregada (falha da API), o painel não deixa guardar, para não gravar o saldo sem eles.
- **Como começar (2026-10-18).** O saldo de hoje é o primeiro passo de quem chega:
  - na tela de entrar, "Como funciona, em 5 passos": (1) informar quanto têm hoje, (2) cadastrar o que se repete,
    (3) lançar e marcar o que foi pago, (4) ver o ano inteiro, (5) trazer a família;
  - em "O ano de vocês", o guia "Como começar" aparece no ano corrente enquanto houver passo pendente (inclusive
    depois de "Recomeçar do zero"):
    - **Informar o saldo** é o único passo **obrigatório** (zero e negativo valem). Abre o painel do saldo ali
      mesmo, só para quem organiza. Depois de guardar, o aviso e o próprio passo dizem que dá para conferir ou
      corrigir na aba Fluxo de caixa (botão "Conferir").
    - **Nada se lança antes do saldo inicial.** Sem saldo informado (em qualquer mês), o servidor recusa lançar,
      lançar parcelado, cadastrar recorrência e confirmar ocorrência (422, com a mensagem de quem precisa agir:
      quem organiza informa o saldo; quem participa é avisado de que essa pessoa precisa fazer isso). Editar e
      excluir o que já existe continua liberado. No front, os formulários de lançamento e de recorrência mostram
      "Primeiro, o saldo de hoje" com o botão "Informar saldo", que abre o painel do saldo; no guia, cadastrar e
      lançar ficam "Depois do saldo".
    - O **último saldo informado não pode ser removido**, só corrigido (pode ser zero): senão a família voltaria a
      ficar sem saldo inicial. "Recomeçar do zero" apaga os saldos e a família recomeça pelo saldo.
    - Cadastrar o que se repete, lançar um gasto e trazer a família (só quem organiza) podem ser **pulados**, com
      "Desfazer". O pulo fica guardado no navegador, por família.
    - Cada passo feito aparece marcado e o próximo pendente em destaque. O guia some quando nada mais está pendente
      (feito ou pulado).
- **Apagar todos os dados (2026-10-18):** em Família, "Recomeçar do zero" (só quem organiza) apaga de vez todo o
  orçamento: lançamentos, recorrências (com meses confirmados e pulados), saldos informados e os lugares do
  dinheiro, investimentos e o plano de contas, que volta a ser o padrão de família nova. Ficam a família, as
  pessoas, os convites, o plano e as contas de cada um. Pede o nome da família digitado e a senha de quem apaga; serve para recomeçar ou para garantir
  que os dados saíram do sistema. Fica registrado na auditoria (sem dado pessoal). Não depende do plano.
- **Por pessoa:** receitas e despesas de cada membro e o peso de cada um no orçamento da família — a clareza do
  impacto de cada um no orçamento do provedor.
- **Simulação de impacto:** quanto um gasto representa no ano (único, diário, por dia útil, semanal, quinzenal,
  mensal… anual): valor no ano, em 5 e 10 anos, % da renda e das despesas da família e quantos dias de renda ele
  consome. Ex.: "R$ 15,00 por dia útil = R$ 3.780,00 no ano (4,2% da renda, 15,3 dias de renda)".

## Investimentos (recurso de plano)
- **Foco (2026-10-24):** o Familiança não é gestor de carteira. Investimentos servem para saber quanto a família tem
  e quanto aplicou no mês (fluxo de caixa e renda dos investimentos), sempre de forma simples e objetiva. Carteira de
  verdade (ativos, preço médio, proventos) fica para o futuro, se fizer sentido.
- **Lançamento pelo saldo do extrato (definido e implementado em 2026-10-24):**
  - Todo mês a família informa o **saldo do extrato** de cada investimento e, se houve, quanto **colocou ou tirou**.
    O rendimento é sempre calculado (saldo − saldo anterior − aporte); mesmo renda fixa descasa por custos, então o
    extrato é a fonte. A **rentabilidade em %** deixa de ser campo (continua como resultado na tabela; o servidor
    segue aceitando e o que já foi registrado com % continua valendo).
  - **Na própria tabela:** clicar no mês de um investimento abre, logo abaixo da linha, o saldo do extrato e o
    "colocou ou tirou" daquele mês, com o resultado na hora ("rendeu R$ 450 (0,9%)") e o aviso de 3%. O mês é a coluna
    clicada (só meses que já chegaram, do primeiro ano com dados em diante). "Atualizar saldos" abre o primeiro que
    falta no mês de referência e, ao salvar, segue para o próximo. (Um painel lateral com todos de uma vez e escolha
    do mês foi testado e trocado por isto no mesmo dia.) A API também grava vários de uma vez. Para "apagar" o
    registro de um mês, só pela API (não há botão de remover na tela).
  - **Atualizado** = cada investimento ativo tem o **saldo do extrato informado** no mês de referência, mesmo que
    igual ao anterior. Não vale comparar valores: saldo igual de verdade deixaria o lembrete preso, e saldo mudado por
    aporte ou % o apagaria sem conferência.
  - **Mês de referência** = o mês anterior (o extrato em mãos no começo de novembro é o de fim de outubro).
  - **Lembretes:** na tela principal (O ano), nos **5 primeiros dias do mês**, "Atualizem os saldos de outubro (2 de
    3)" com o atalho para o formulário, até todos estarem atualizados. Na tela de Investimentos, o aviso fica enquanto
    faltar algum saldo do mês de referência, sem limite de dias. Sem e-mail ou notificação por enquanto.
- **Encerrar = resgatou tudo (2026-10-24):** "Encerrar" pergunta o mês do resgate (vem o atual; não pode ser
  futuro, antes do primeiro registro nem com saldo informado depois). Nesse mês o saldo vira zero e o saldo do mês
  anterior volta para a conta como resgate (aplicação negativa no fluxo de caixa), sem rendimento; dali em diante o
  investimento fica zerado e sai do total. Antes, encerrar só escondia: o saldo seguia somando na carteira e na renda
  dos investimentos. Para voltar a acompanhar, "Reativar" e informar o saldo (entra como abertura). Quem já tinha
  encerrado do jeito antigo continua com o saldo: reativar e encerrar de novo, com o mês do resgate.
  - Gravar o saldo de um mês substitui o registro dele inteiro: pela API, mandar o saldo sem o `aporte` apaga o
    aporte (ou resgate) já registrado naquele mês. A tela sempre manda os dois, já preenchidos.
- **No menu principal (2026-10-24):** Investimentos foi para a barra principal e "Quanto pesa?" para o menu do nome.
- Carteira da família; por mês e investimento: aporte (negativo = resgate), rentabilidade do mês (%) e,
  opcionalmente, o saldo do extrato, que prevalece sobre o calculado.
- Saldo do mês = saldo anterior + rendimento (rentabilidade × saldo anterior) + aporte.
- **Saldo de abertura (2026-10-24):** o saldo do extrato informado num investimento **zerado** (o primeiro registro,
  ou a volta depois de resgatar tudo) menos o aporte do mês é **abertura**: o dinheiro já existia, então não é rendimento nem aporte (não entra no fluxo de
  caixa). Antes, uma carteira de R$ 50 mil cadastrada no primeiro dia virava "R$ 50 mil de rendimento" e inflava a
  renda dos investimentos e o grau de independência. Regra automática, sem campo novo; corrige os dados que já
  existem (o cálculo é feito a cada consulta). A tabela mostra "abertura R$ X em mês/aa" sob o investimento.
- **Salto de saldo (2026-10-24):** ao informar um saldo do extrato cujo rendimento implícito passa de 3% do saldo
  anterior (para mais ou para menos), o formulário avisa e sugere informar o aporte ou o resgate, sem bloquear.
- **No fluxo de caixa só entra o realizado (2026-10-24):** a linha "Aplicações nos investimentos" mostra só dinheiro
  que de fato saiu da conta para os investimentos (aplicação, positivo: sai do disponível) ou voltou deles (resgate,
  negativo: soma no disponível do mês). Rendimento e prejuízo que ficam dentro do investimento (não realizados) não
  entram no fluxo; aparecem só na tela de Investimentos. A tela diz isso sob o rótulo ("negativo = resgate, voltou
  para a conta"), no mouse de cada valor negativo e numa nota abaixo da tabela.
- Os aportes entram no fluxo de caixa como "aplicação total no mês"; os rendimentos são a "renda dos
  investimentos". **Grau de independência financeira** = renda dos investimentos ÷ despesas fixas.
- **Meta de aporte** da família (% do que sobra no mês, renda − despesas) gera o "aporte previsto" de cada mês.
- Rentabilidade da carteira por mês e acumulada no ano.

## Despesas PJ (recurso de plano)
- Receitas marcadas como **tributáveis** (ex.: notas fiscais emitidas) formam a base de cálculo.
- Despesas com **alíquota** (Simples/DAS, ISS, PIS, IRPJ, CSLL) são calculadas todo mês como % das receitas
  tributáveis do mês; não recebem lançamentos.
- Grupo da **empresa** mostra o peso sobre as receitas tributáveis (o "% despesa fixa" da planilha).
- Modelo PJ pronto (receita tributável + grupo da empresa com os impostos e custos fixos), aplicado sob demanda.
- **Nota fiscal (2026-10-22):** o lançamento de receita tributável guarda, além da data, a **competência** (mês do
  serviço) e a **data de emissão** da nota, ambas opcionais. A data do lançamento é a do **pagamento**, e é ela que
  decide o mês (renda, fluxo e base das alíquotas). Na recorrência de nota, a data do pagamento é informada a cada
  mês ao confirmar (o servidor exige; não vale o dia previsto). Competência e emissão só valem em categoria tributável.

# Planos e assinatura

| Plano | Pessoas na família | Recursos |
|---|---|---|
| Gratuito | 2 | Núcleo: orçamento, recorrências, visão anual, inflação, fluxo de caixa, visão por pessoa, impacto |
| Família | 6 | + Investimentos |
| Premium | 12 | + Despesas PJ |

- Família nova: Premium em teste por 30 dias. Assinatura vencida ou cancelada volta ao Gratuito (os dados ficam;
  o recurso só fica indisponível).
- Enquanto não há cobrança integrada, o ADMIN da plataforma ajusta a assinatura das famílias pela API.
- Preços e nomes dos planos são provisórios.
- **Segurança (2026-10-17):** avaliado contra os 13 requisitos mínimos ([`docs/seguranca.md`](../docs/seguranca.md)).
  Entraram na v1: verificação em duas etapas **opcional** por aplicativo autenticador (com códigos de recuperação),
  sessão de no máximo 12 h, limite geral de requisições, limpeza de texto livre e aviso de segredos fracos em
  produção. MFA e segredos **não bloqueiam** a subida: sem `MFA_CHAVE` o MFA fica desligado; segredo fraco só avisa.

# Pendências e planos futuros

- **Segurança:** fases 1 a 3 do plano em [`docs/seguranca.md`](../docs/seguranca.md). Antes do deploy: script com
  backup e rollback, tela do MFA no front. Antes de abrir para clientes: MFA obrigatório para ADMIN e e-mail
  transacional. Depende de infra: gerenciador de segredos e rotação.
- **Frontend — próximos passos** (v1 pronta; contexto em [`PRODUCT.md`](../PRODUCT.md)):
  - Usar no dia a dia da família e ajustar as telas com o que aparecer.
  - Tela de administração das famílias (hoje só pela API) e landing page em familianca.com.br. (O modo escuro já
    existe: Minha conta → Aparência.)
  - Logo final: o front usa um símbolo provisório de 12 barras (`frontend/src/componentes/Marca.vue`).
- **Marca:** validar com 5 a 8 famílias, depositar o pedido de registro de "Familiança" no INPI (classes 9 e 36;
  busca de 2026-10-17 sem nenhum registro igual ou parecido) e finalizar o logo com um designer. Domínio
  familianca.com.br já registrado.
- **Deploy** no VPS do controle-alugueis: escrever o guia (`deploy_vps.md`), domínio (familianca.com.br /
  .app), proxy, backup.
- **Cobrança recorrente:** integração com gateway de pagamento, webhooks atualizando a assinatura, página de planos.
- **E-mail:** enviar o convite por e-mail (hoje o link é copiado e compartilhado), confirmação de e-mail no cadastro
  e "esqueci minha senha".
- **App mobile:** ligar a sessão por header (`X-Auth-Token`) já preparada e o login do app.
- **Uma pessoa em mais de uma família** (hoje é uma só) e "sair da família".
- **Excluir a conta e a família inteira** (LGPD): hoje dá para apagar o orçamento ("Recomeçar do zero"); falta
  excluir pessoas, convites, família e contas, com confirmação por e-mail, e exportar os dados antes.
- **Resumo da tela de Investimentos:** a frase "foram aplicados" mostra o líquido (fica negativo com resgate) e não
  cita as aberturas; separar "aplicados" de "resgatados" e dizer com quanto a carteira abriu.
- **Contas a pagar — depois do checklist (feito em 2026-10-24):** lembretes de vencimento (e-mail/WhatsApp),
  candidato a recurso de plano pago; selo "faltam N" na faixa do mês da visão anual; agrupar as contas do cartão na
  fatura (depende de cartões).
- **Cartões de crédito:** cartão no lançamento e visão da fatura (a planilha controla faturas). A forma de pagamento
  "no cartão de crédito" das recorrências já existe; falta dizer qual cartão e montar a fatura com vencimento.
- **Importar a planilha** (xlsx) de anos anteriores para o histórico. Já testado com um script fora do repositório
  que usa só a API (2026-10-17, ver "Aprendizados da carga da planilha"). Como produto, precisa de uma rota de
  importação em lote: pela API comum foram ~1.500 chamadas, 6 min com o teto de 300 req/min.

## Aprendizados da carga da planilha (2026-10-17)

Carga feita só pela API (cadastro de categorias, lançamentos, recorrências com confirmação e pulo, parcelados,
saldos e investimentos). Renda, despesas e sobra do mês conferem com a planilha mês a mês nos 5 anos. Como foi
mapeado: anos fechados viram lançamentos; no ano atual, o que se repete virou recorrência (meses passados
confirmados com o valor real, futuros previstos) e pagamentos anuais (IPTU, IPVA, seguros) viraram recorrência
anual. Faturas de cartão ficaram num grupo "Cartões de crédito" (uma categoria por cartão), por falta do detalhe.

- ~~**Bug — saldo informado vira rendimento**~~: resolvido em 2026-10-24 (saldo de abertura e aviso de salto, ver
  Investimentos).
- **Alíquota com vigência:** o DAS da planilha varia de ~7,7% a ~10% das notas ao longo dos anos; com uma alíquota
  única o histórico ficaria errado, então o DAS foi lançado valor a valor (a categoria perdeu a alíquota).
- **Inflação mensal com gasto concentrado** (fatura grande, IR, reforma) oscila de −70% a +260% e não ajuda;
  avaliar inflação só das despesas fixas/recorrentes ou média móvel de 12 meses (o acumulado e o mês em andamento
  já foram resolvidos em 2026-10-24: só mês fechado).
- **Meta de aporte por mês:** a planilha muda o % ao longo do ano (30% → 50%); o sistema tem uma meta só.
- **Cartões:** as faturas sem detalhe são o maior gasto do ano atual, o que reforça a pendência de cartões.
- Orçamento planejado × realizado por categoria (meta de gasto).
- Notas fiscais PJ por cliente (aba "NF Pjs" da planilha). O saldo por conta (aba "Saldo CC") entrou como saldo
  lugar a lugar; falta a evolução mês a mês de cada lugar.
