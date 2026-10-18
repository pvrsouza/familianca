-- Saldo de conferência: com o saldo do dia 1º informado, a família pode guardar também o de outro dia (ex.: hoje)
-- para conferir. Ele não muda o fluxo: a tela compara com o dia 1º + o lançado até o dia e mostra a diferença.
-- Só existe junto do saldo do dia 1º (sem data_referencia): o saldo de um dia sozinho já vira o do dia 1º.
-- Reverter: alter table saldo_mes drop constraint ck_saldo_conferencia, drop column conferencia_dia,
-- drop column conferencia_valor;
alter table saldo_mes add column conferencia_dia date;
alter table saldo_mes add column conferencia_valor numeric(14,2);
alter table saldo_mes add constraint ck_saldo_conferencia check (
    (conferencia_dia is null and conferencia_valor is null)
    or (conferencia_dia is not null and conferencia_valor is not null and data_referencia is null));
