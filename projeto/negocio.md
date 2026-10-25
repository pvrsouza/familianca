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
| H4 | Open Finance é necessário para reter (lançar à mão cansa). | Frequência de lançamento após 30 e 60 dias; custo por conexão × preço do plano. | 🔴 |
| H5 | O convite pelo WhatsApp é um canal de aquisição (membros viram organizadores de outra família). | Origem dos cadastros novos. | 🔴 |
| H6 | Privacidade (sem anúncio, sem venda de dados, dados no Brasil) pesa na decisão de pagar, contra apps gratuitos que vivem de dados e o app do banco. | Nas entrevistas, perguntar o que impede pôr o orçamento da família num app; ver se a promessa muda a disposição a pagar. | 🔴 |

# Registro de iterações

Uma entrada por entrega, a mais recente primeiro: o que mudou no produto, o que isso muda nas hipóteses, decisões
tomadas e perguntas que ficaram.

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
