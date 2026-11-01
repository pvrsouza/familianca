# Modelo de negócio

O Familiança como startup: para quem, por que pagam, quanto e como chegam até nós. Tudo aqui é **hipótese** até ser
validado com famílias de fora. Revisado a cada entrega pela skill `/iteracao-negocio` (ver `CLAUDE.md`). Regras do
produto continuam em [`projeto.md`](projeto.md); marca e personas em [`marca.md`](marca.md).

Não inventar números (preços finais, tamanho de mercado, conversão): o que não tiver fonte fica como "a levantar".

# Canvas (v1, 2026-10-25)

| Bloco | Hipótese atual |
|---|---|
| **Problema** | Planilha que só uma pessoa entende; apps feitos para uma pessoa e para o mês; ninguém vê o ano nem o peso de cada gasto; quem sustenta a casa se preocupa sozinho. |
| **Segmento** | Famílias brasileiras classe B/C, com quem organiza (já usa planilha) e quem participa (parceiro, filhos). Parte com renda PJ. Primeiros a adotar: quem mantém uma planilha anual e cansou de mantê-la sozinho. |
| **Proposta de valor** | "O orçamento é de todos": o ano inteiro da família (previsto × realizado) e o impacto de cada gasto no ano, com todos participando sem sensação de vigilância. |
| **Solução** | Visão anual em formato de planilha, recorrências previstas, convite por link, papéis, visão por pessoa, inflação familiar. |
| **Canais** | A levantar. Candidatos: o próprio convite (cada família traz 2 a 6 pessoas), WhatsApp, conteúdo sobre orçamento anual (virada do ano, IR), indicação. |
| **Receita** | Vendida pela CSS Consultoria em TI Ltda. (ME, Simples, CNAE 6203-1/00), que emite a nota. Assinatura mensal por família em três planos (Gratuito, Família, Premium; ver `projeto.md`), 30 dias de Premium no cadastro. Candidatos a recurso pago: Open Finance, lembretes de contas, PJ. Preços a definir. |
| **Custos** | VPS (KingHost na validação, AWS se der certo); backup no Cloudflare R2; gateway de pagamento (% por cobrança); integrador Open Finance (custo por conexão); e-mail transacional; suporte; revisão jurídica da política de privacidade (a levantar). |
| **Métricas** | Famílias ativas, membros por família, conversão do teste Premium para pago, retenção mensal. |
| **Vantagem difícil de copiar** | A levantar. Hoje: foco em família + ano (os concorrentes são pessoa + mês) e o histórico da família acumulado no produto. Candidata (H6): privacidade como promessa — sem anúncio, sem venda de dados, dados no Brasil, empresa identificada. |

# Hipóteses em aberto

Uma linha por hipótese, da mais arriscada para a menos. Status: 🔴 não testada, 🟡 com sinais, 🟢 validada, ⚫ descartada.

| # | Hipótese | Como validar | Status |
|---|---|---|---|
| H1 | Quem organiza consegue fazer os outros membros usarem (não vira planilha de uma pessoa só). | Membros ativos por família nas primeiras 5 a 8 famílias. | 🔴 |
| H2 | Famílias pagam por visão anual + colaboração, mesmo com apps gratuitos e o app do banco. | Entrevistas e conversão do teste Premium. | 🔴 |
| H3 | Investimentos e PJ são o que separa os planos (e não o número de pessoas). | Quais recursos as famílias em teste usam antes de cair para o Gratuito. | 🔴 |
| H4 | Open Finance é necessário para reter (lançar à mão cansa). | Frequência de lançamento após 30 e 60 dias; custo por conexão × preço do plano. **Teste barato desde 2026-10-31:** quantas famílias importam extrato OFX, com que frequência (tabela `importacao_lote`) e quantas linhas por mês. | 🔴 |
| H5 | O convite pelo WhatsApp é um canal de aquisição (membros viram organizadores de outra família). | Origem dos cadastros novos. | 🔴 |
| H6 | Privacidade (sem anúncio, sem venda de dados, dados no Brasil) pesa na decisão de pagar, contra apps gratuitos que vivem de dados e o app do banco. | Nas entrevistas, perguntar o que impede pôr o orçamento da família num app; ver se a promessa muda a disposição a pagar. | 🔴 |

# Registro de iterações

- **2026-10-31 (conta em cada lançamento):** o lançamento ganha conta opcional (campo no formulário, com a última
  usada já preenchida, rótulo na lista e filtro por conta). Serve para a família conferir o app contra o extrato do
  banco. **Efeito nas hipóteses:** toca a **H4** (lançar à mão cansa): conferir com o extrato é parte do custo de
  manter o orçamento, e a conta liga o lançamento digitado ao importado. Também prepara, sem prometer, o saldo
  calculado por conta, que seria um motivo para pagar. **Canvas:** nenhum bloco muda; sem recurso pago e sem custo.
  **Crítica:** conta opcional pode nunca ser preenchida; se a maioria dos lançamentos novos continua sem conta, o
  filtro vale pouco e a ideia de calcular saldo por conta não se sustenta. **Atualização no mesmo dia:** a recorrência também ganhou conta (as confirmações do mês, até as automáticas,
  nascem nela), porque as contas fixas (condomínio, aluguel) são as que mais se confere com o extrato; sem isso o
  campo sumia nas categorias recorrentes. **Pergunta aberta (H4):** que parte dos
  lançamentos manuais novos tem conta depois de 30 dias, e quem preenche também confere com o extrato? Se for baixa,
  perguntar nas entrevistas se a conta é o que falta ou se o problema é outro.
- **2026-10-31 (destaque do que ficou sem categoria):** o lançamento na categoria "A classificar" (a que a
  importação usa quando não sabe classificar) agora leva selo e faixa na lista, e um aviso conta quantos há no
  período, com atalho para ver só esses. A marca passou a ser da categoria, não do nome. **Efeito nas hipóteses:**
  toca a **H4**: se importar troca "digitar" por "classificar", a fricção só mudou de lugar; o destaque deixa a
  pessoa ver e fechar essa fila, e dá uma medida nova. **Canvas:** nenhum bloco muda; sem recurso pago, sem custo.
  **Crítica:** o destaque ajuda a resolver, mas não resolve sozinho; sem regras aprendidas de categoria, quem importa
  todo mês reclassifica as mesmas descrições. **Pergunta aberta (H4):** que parte das linhas importadas continua
  "sem categoria" 7 dias depois, e quem importa uma segunda vez classifica menos (a sugestão pela última descrição
  funciona)? Se a fila não cai, "regras aprendidas" sobe na lista antes do Open Finance.

- **2026-10-31 (importação de extrato OFX/OFC):** a família envia o extrato do banco ou do cartão, vê o que é novo, o
  que já foi importado e o que parece duplicado, e importa só o que quiser; a linha que bate com uma conta
  recorrente pendente confirma a conta em vez de criar outro lançamento. Liberado a todos os planos na v1.
  **Efeito nas hipóteses:** é o teste mais barato da **H4** (lançar à mão cansa; Open Finance seria necessário para
  reter). OFX não custa por conexão e a família continua no controle (arquivo descartado, sem acesso contínuo à
  conta). Se as famílias importam todo mês, a fricção é real e o Open Finance tem um mercado; se não importam, o
  problema não era digitar. Toca também a **H6** (privacidade): "o arquivo é lido e descartado" é uma promessa
  verificável. **Canvas:** proposta de valor (menos trabalho para lançar), custos (nenhum novo; sem fornecedor) e
  receita (decisão: sem recurso pago por ora, porque ainda não há famílias de fora para testar disposição a pagar, e
  travar esconderia a hipótese principal). **Hipótese mais arriscada afetada:** H4. **Crítica:** o risco é medir
  intenção, não hábito: importar uma vez para carregar o histórico não prova que a fricção mensal some. Baixar o
  OFX no app do banco já é um atrito que pode derrubar o uso. **Perguntas abertas:** depois de 30 dias, a família
  importa de novo ou volta a digitar? Quais bancos e cartões não exportam OFX (o Nubank, por exemplo, só exporta
  CSV, a confirmar com as famílias)? Se muitos exportarem só CSV, a v2 deixa de ser opcional. **Próxima ação:**
  nas 5 a 8 famílias de validação, pedir que importem o extrato do mês e contar, no `importacao_lote`, quantas
  voltam no mês seguinte; perguntar o banco de cada uma para priorizar o CSV.
Uma entrada por entrega, a mais recente primeiro: o que mudou no produto, o que isso muda nas hipóteses, decisões
tomadas e perguntas que ficaram.

- **2026-10-31 (hash de senha migrável):** infraestrutura de segurança (prefixo `{bcrypt}` e regravação no login), sem
  efeito no produto visível, nas hipóteses, nos planos nem no custo. Só tira o risco de ter de forçar reset de senha em
  massa se for preciso trocar de algoritmo depois de abrir a outras famílias. Nada a testar com famílias.
- **2026-10-31 (limite de login por IP):** segurança da fase 2, sem efeito no produto visível, nos planos nem no
  custo (bucket em memória). Não toca em nenhuma hipótese. Só reduz o risco de credential stuffing antes de abrir o
  produto a outras famílias, quando uma conta invadida custaria confiança (e LGPD). Risco novo de operação: no deploy
  o proxy precisa sobrescrever o `X-Forwarded-For`, ou o limite fica forjável. **Próxima pergunta a testar:** a mesma
  de antes (H3, famílias de fora entendem "aplicados" e "resgatados"); nada novo a validar com famílias.
- **2026-10-30 (faixa de ano e ordem na API):** endurecimento de validação de entrada, sem efeito no produto
  visível, nas hipóteses, nos custos ou nos planos. Nada a testar com famílias; só reduz risco antes de abrir a API
  (app mobile) a quem não seja a nossa tela.
- **2026-10-30 (resumo de Investimentos):** a frase do topo da tela separa aplicado, resgatado e abertura do ano
  (contados por investimento). Correção de texto, sem recurso novo, preço ou custo. **Efeito nas hipóteses:** nenhum
  direto; só reforça que a tela de Investimentos (H3) diz a verdade quando há resgate, o que importa antes de
  mostrá-la a famílias de fora. **Pergunta aberta (a testar nas primeiras famílias, H3):** a família entende
  "aplicados" e "resgatados" quando move dinheiro de um investimento para outro, ou prefere ver só o líquido? A
  transferência aparece nos dois lados por decisão desta entrega.
- **2026-10-25 (LGPD):** avaliação de LGPD (`.lgpd/`) e correções pequenas: backup remoto com 30 dias, convites
  apagados 30 dias depois de expirar, registro de acesso de 6 meses (Marco Civil), log sem texto digitado e as telas
  de acesso com a identificação da empresa. **Decisão:** quem vende é a CSS Consultoria em TI Ltda. (CNPJ, ME no
  Simples, CNAE de licenciar software), não a pessoa física: emite nota da assinatura, não expõe CPF e endereço de
  casa, separa o patrimônio; em troca, a guarda de 6 meses do Marco Civil vira obrigação. **Efeito nas hipóteses:**
  nenhuma validada; o produto fica pronto, do lado legal, para receber as primeiras famílias de fora (H1 e H2), desde
  que a política seja publicada. Nova hipótese H6: privacidade como argumento de venda. **Custos:** revisão jurídica
  da política entra no canvas. **Perguntas abertas:** quanto custa a revisão jurídica e se dá para adiar até a
  primeira cobrança; se as famílias de validação (5 a 8, conhecidas) precisam da política publicada antes de entrar
  (recomendação: sim, a versão revisada, porque os dados financeiros já são reais).
- **2026-10-25:** canvas v1 escrito a partir de `projeto.md`, `PRODUCT.md` e `marca.md`. Nada validado com famílias
  de fora ainda.
