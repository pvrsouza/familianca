-- A conta (lugar onde está o dinheiro) de cada lançamento. Opcional: o histórico digitado à mão fica sem conta.
-- Diferente de importacao_local_saldo_id, que só serve de chave contra duplicata na importação.
alter table lancamento add column local_saldo_id bigint references local_saldo (id);

-- Os importados já sabem a conta.
update lancamento set local_saldo_id = importacao_local_saldo_id where importacao_local_saldo_id is not null;

create index lancamento_local_saldo_idx on lancamento (local_saldo_id) where local_saldo_id is not null;
