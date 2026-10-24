-- Regra de previsão a partir de um mês: a regra própria da categoria ou da conta que se repete pode mudar a partir de
-- um mês futuro. estimativa_desde é esse mês (AAAA-MM) e estimativa_antes a regra que vale até lá (nulo = a de cima:
-- a família, para a categoria; a categoria, para a conta). Sem estimativa_desde, a regra vale para todos os meses.
-- Rollback: as colunas novas são ignoradas pela versão anterior (que passa a usar a regra nova em todos os meses).
alter table categoria add column estimativa_desde varchar(7) check (estimativa_desde ~ '^[0-9]{4}-(0[1-9]|1[0-2])$');
alter table categoria add column estimativa_antes varchar(10)
    check (estimativa_antes in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));

alter table recorrencia add column estimativa_desde varchar(7) check (estimativa_desde ~ '^[0-9]{4}-(0[1-9]|1[0-2])$');
alter table recorrencia add column estimativa_antes varchar(10)
    check (estimativa_antes in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));
