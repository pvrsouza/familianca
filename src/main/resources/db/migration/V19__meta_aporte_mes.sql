-- Meta de aporte mês a mês:
-- * familia.estimativa_aporte: regra para estimar a meta dos meses sem meta definida (média dos últimos 3 meses por
--   padrão), a partir do % realizado dos meses fechados; a meta única (meta_aporte_percentual) vira a meta de partida;
-- * familia_meta_aporte: meta definida à mão por mês.
-- Aditiva. Rollback: a versão anterior do app ignora a coluna e a tabela e volta à meta única em todo mês.
alter table familia add column estimativa_aporte varchar(10) not null default 'MEDIA_3M'
    check (estimativa_aporte in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));

create table familia_meta_aporte (
    familia_id  bigint       not null references familia (id) on delete cascade,
    competencia varchar(7)   not null check (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    percentual  numeric(5,2) not null check (percentual between 0 and 100),
    primary key (familia_id, competencia)
);
