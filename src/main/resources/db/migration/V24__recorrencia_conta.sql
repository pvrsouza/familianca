-- A conta (lugar onde está o dinheiro) de uma recorrência: cada ocorrência confirmada, inclusive a que o sistema
-- confirma sozinho, nasce com essa conta. Opcional.
alter table recorrencia add column local_saldo_id bigint references local_saldo (id);

create index recorrencia_local_saldo_idx on recorrencia (local_saldo_id) where local_saldo_id is not null;
