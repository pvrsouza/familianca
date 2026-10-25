# Bases legais por atividade de tratamento

**Última atualização**: 2026-10-25 · fonte das atividades: [`data-map.md`](data-map.md)

Nenhuma atividade usa consentimento (Art. 7º, I): tudo o que o Familiança trata é necessário para entregar o
serviço contratado ou para protegê-lo. Por isso não há "aceitar cookies" nem caixas de consentimento; o cadastro
só precisa registrar a **ciência** da política (versão + data), que é transparência (Art. 9º), não base legal.

## A01 — Conta e autenticação {#a01}

- **Finalidade**: identificar quem entra e proteger o acesso (login, MFA, troca de senha).
- **Dados tratados**: nome, e-mail, hash BCrypt da senha, segredo TOTP cifrado, hash dos códigos de recuperação.
- **Sensíveis?**: Não.
- **Base legal**: Art. 7º, V — execução de contrato.
- **Justificativa**: sem conta não há serviço; o titular pede o cadastro.
- **Retenção**: enquanto a conta existir; excluída junto com a conta (pendente, ver `gaps.md` G02).
- **Revogação possível?**: não se revoga, mas o titular pode excluir a conta (Art. 18, VI).
- **Última revisão**: 2026-10-25

## A02 — Família e convites {#a02}

- **Finalidade**: montar o grupo da família e dar acesso a cada pessoa convidada.
- **Dados tratados**: vínculo membro↔família, papel (DONO/MEMBRO); no convite, o e-mail da pessoa convidada.
- **Sensíveis?**: Não.
- **Base legal**: membros — Art. 7º, V (contrato). E-mail de quem **ainda não aceitou** — Art. 7º, IX
  (legítimo interesse do DONO e do Familiança em convidar), com LIA abaixo.
- **Justificativa**: a pessoa convidada ainda não é parte do contrato; o e-mail é o mínimo para o convite existir.
- **LIA (resumo)**: *finalidade legítima* — convite pedido por quem já é cliente; *necessidade* — só o e-mail,
  sem envio automático (o link é compartilhado pelo DONO); *balanceamento* — expectativa razoável de quem recebe
  um convite de um familiar; *salvaguardas* — expiração do convite e remoção do e-mail depois (G06), cancelamento
  pelo DONO, transparência na tela de aceite.
- **Retenção**: o convite (com o e-mail) é apagado 30 dias depois de expirar, em qualquer situação (limpeza diária).
- **Revogação possível?**: a pessoa pode pedir a exclusão do e-mail (Art. 18, VI).
- **Última revisão**: 2026-10-25

## A03 — Orçamento da família {#a03}

- **Finalidade**: o serviço em si — lançamentos, recorrências, saldos, investimentos, metas, visão anual.
- **Dados tratados**: valores, datas, categorias, descrições em texto livre, quem lançou (membro), notas fiscais PJ.
- **Sensíveis?**: Não por finalidade. **Atenção**: a descrição livre pode **revelar** dado sensível por
  inferência (ex.: "psiquiatra", "dízimo da igreja", "sindicato"). O Familiança não usa esses dados para nada além
  de mostrar à própria família, não faz perfil nem classificação automática deles.
- **Base legal**: Art. 7º, V — execução de contrato.
- **Justificativa**: é a funcionalidade contratada. Se no futuro houver categorização automática ou Open Finance,
  reavaliar (Art. 9º, § 2º) e considerar Art. 11 para o que for sensível.
- **Retenção**: enquanto a família existir; "Apagar todos os dados" já remove; exclusão da família pendente (G02).
- **Revogação possível?**: exclusão a qualquer momento pelo DONO.
- **Última revisão**: 2026-10-25

## A04 — Segurança, sessão e logs {#a04}

- **Finalidade**: manter a sessão, limitar tentativas, investigar abuso e incidentes.
- **Dados tratados**: id da sessão, id do usuário, IP, rota acessada, horário, eventos de auditoria (sem e-mail
  nem nome). O log de requisições grava a query string (ver G08).
- **Sensíveis?**: Não.
- **Base legal**: Art. 7º, IX — legítimo interesse (segurança do próprio titular e do serviço); sessão também
  Art. 7º, V. Registro de acesso (IP + data/hora): Art. 7º, II — obrigação legal do Marco Civil (Lei 12.965/2014,
  Art. 15), porque o controlador é pessoa jurídica.
- **LIA (resumo)**: finalidade de proteção do titular (Art. 10, II); só id e IP; retenção curta; sem uso para
  outra finalidade.
- **Retenção**: sessão até 30 min de inatividade ou 12 h; contadores de tentativa em memória (minutos); logs
  do Docker rotacionados por tamanho (G07); registro de acesso (`registro_acesso`) 6 meses, limpeza diária.
- **Última revisão**: 2026-10-25

## A05 — Assinatura e plano {#a05}

- **Finalidade**: saber o plano da família, o teste e a validade, e liberar recursos pagos.
- **Dados tratados**: plano, situação, validade (por família). Hoje sem dado de pagamento.
- **Sensíveis?**: Não.
- **Base legal**: Art. 7º, V — contrato. Quando houver cobrança: Art. 7º, II (obrigação fiscal) para notas e
  registros de pagamento.
- **Retenção**: com a família; registros fiscais pelo prazo legal (5 anos, CTN art. 173/174) quando existirem.
- **Última revisão**: 2026-10-25

## A06 — Backups {#a06}

- **Finalidade**: recuperar os dados em caso de falha ou incidente.
- **Dados tratados**: cópia integral do banco.
- **Base legal**: a mesma das atividades copiadas + Art. 46 (dever de segurança).
- **Retenção**: 3 cópias no VPS; remoto (Cloudflare R2) 30 dias.
- **Última revisão**: 2026-10-25

## A07 — Preferências no navegador {#a07}

- **Finalidade**: lembrar tema, centavos, inflação, valores ocultos, categorias abertas e guia pulado.
- **Dados tratados**: preferências no `localStorage`; a chave do guia leva o id da família. Cookies só de sessão
  e CSRF.
- **Base legal**: Art. 7º, V — estritamente necessários ao serviço; não há rastreamento nem terceiros.
- **Retenção**: no aparelho, até o usuário limpar.
- **Última revisão**: 2026-10-25

## A08 — Suporte e administração {#a08}

- **Finalidade**: o ADMIN da plataforma ajusta a assinatura e dá suporte.
- **Dados tratados**: dados de família e conta acessíveis pela API de admin.
- **Base legal**: Art. 7º, V — contrato.
- **Retenção**: não guarda dado próprio além da auditoria.
- **Última revisão**: 2026-10-25
