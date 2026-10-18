-- Estimativa dos próximos meses para despesas que não se repetem (média, máximo ou mínimo dos meses que já
-- passaram no ano). A família tem a regra padrão (começa pela média); a categoria pode ter a sua (nulo = segue a
-- família). Colunas com padrão ou anuláveis: a versão anterior do app continua funcionando.
alter table familia add column estimativa varchar(10) not null default 'MEDIA'
    check (estimativa in ('NENHUMA', 'MEDIA', 'MAXIMO', 'MINIMO'));
alter table categoria add column estimativa varchar(10)
    check (estimativa in ('NENHUMA', 'MEDIA', 'MAXIMO', 'MINIMO'));
