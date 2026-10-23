-- Valor previsto diferente só numa ocorrência (a conta deste mês veio mais cara ou mais barata), sem confirmar nada.
-- Tabela nova (aditiva). Rollback: a versão anterior do app ignora a tabela e volta a prever o valor cadastrado.
create table recorrencia_valor (
    recorrencia_id bigint        not null references recorrencia (id) on delete cascade,
    competencia    varchar(7)    not null check (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    valor          numeric(12,2) not null check (valor > 0),
    primary key (recorrencia_id, competencia)
);
