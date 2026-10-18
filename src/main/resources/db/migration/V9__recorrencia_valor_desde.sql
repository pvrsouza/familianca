-- Competência (AAAA-MM) da última mudança do valor cadastrado da recorrência. A estimativa do previsto (média,
-- máximo ou mínimo das confirmações do ano) só considera as confirmações a partir dela, para um reajuste não ser
-- desfeito pela média dos meses de antes. Nulo = desde sempre. Coluna anulável: a versão anterior continua funcionando.
alter table recorrencia add column valor_desde varchar(7);
