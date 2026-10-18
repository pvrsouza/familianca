-- Saldo informado com o dia a que ele se refere (ex.: "o saldo de hoje, 18/10"). Sem dia, o valor já é o do dia 1º.
-- No fluxo o saldo vale sempre como o do dia 1º: com dia, o sistema desfaz o que foi lançado de 1º até ele.
-- Reverter: alter table saldo_mes drop column data_referencia (os saldos com dia passam a valer como do dia 1º).
alter table saldo_mes add column data_referencia date;
