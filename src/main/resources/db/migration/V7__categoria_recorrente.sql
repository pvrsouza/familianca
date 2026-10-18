-- Categoria que costuma se repetir todo mês (aluguel, telefone, salário): ao lançar nela, o app sugere repetir o
-- valor no ano (cria a recorrência). Coluna com padrão: a versão anterior do app continua funcionando.
alter table categoria add column recorrente boolean not null default false;
