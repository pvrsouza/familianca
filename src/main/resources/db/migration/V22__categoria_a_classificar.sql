-- Marca a categoria que a importação de extrato usa para o que não foi classificado. Antes o app a achava pelo
-- nome "A classificar"; com a marca, renomear a categoria não a perde. As que já existem com esse nome são marcadas.
alter table categoria add column a_classificar boolean not null default false;

update categoria set a_classificar = true where lower(nome) = 'a classificar';
