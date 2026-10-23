-- Estimativa dos próximos meses:
-- * nova regra MEDIA_3M (média dos últimos 3 meses, mesmo que do ano anterior), que vira o padrão da família: quem
--   seguia o padrão (MEDIA) passa a ela; categorias com regra própria não mudam;
-- * recorrencia.estimativa: regra própria da conta que se repete (nulo = segue a categoria).
-- Rollback: a versão anterior do app não conhece MEDIA_3M; antes de voltar, rode
--   update familia set estimativa = 'MEDIA' where estimativa = 'MEDIA_3M';
--   update categoria set estimativa = 'MEDIA' where estimativa = 'MEDIA_3M';
-- (a coluna nova de recorrencia é ignorada pela versão anterior).
alter table familia drop constraint familia_estimativa_check;
alter table familia add constraint familia_estimativa_check
    check (estimativa in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));
alter table familia alter column estimativa set default 'MEDIA_3M';
update familia set estimativa = 'MEDIA_3M' where estimativa = 'MEDIA';

alter table categoria drop constraint categoria_estimativa_check;
alter table categoria add constraint categoria_estimativa_check
    check (estimativa in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));

alter table recorrencia add column estimativa varchar(10)
    check (estimativa in ('NENHUMA', 'MEDIA_3M', 'MEDIA', 'MAXIMO', 'MINIMO'));
