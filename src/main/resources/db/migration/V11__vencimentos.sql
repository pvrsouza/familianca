-- Vencimento das contas que se repetem (e das avulsas).
-- * sem_vencimento: a conta não tem data certa para pagar; o dia só posiciona a ocorrência no mês.
-- * forma_pagamento: como é paga (BOLETO_PIX, DEBITO_AUTOMATICO, CARTAO_CREDITO); nulo = não informado.
-- * frequência UNICA: conta avulsa, uma vez só no mês de início.
-- * recorrencia_vencimento: vencimento diferente só numa ocorrência (o boleto deste mês veio para outro dia).
-- Tudo aditivo (colunas com padrão, tabela nova). Rollback: a versão anterior do app funciona com este schema, mas
-- não conhece a frequência UNICA; só volte de versão sem contas avulsas cadastradas (ou converta-as antes).
alter table recorrencia add column sem_vencimento boolean not null default false;
alter table recorrencia add column forma_pagamento varchar(20)
    check (forma_pagamento in ('BOLETO_PIX', 'DEBITO_AUTOMATICO', 'CARTAO_CREDITO'));

alter table recorrencia drop constraint recorrencia_frequencia_check;
alter table recorrencia add constraint recorrencia_frequencia_check
    check (frequencia in ('UNICA', 'MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'));

create table recorrencia_vencimento (
    recorrencia_id bigint     not null references recorrencia (id) on delete cascade,
    competencia    varchar(7) not null check (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    vencimento     date       not null,
    primary key (recorrencia_id, competencia)
);
