-- Confirmação automática por recorrência (débito automático, conta de valor fixo): data em que foi ligada.
-- As ocorrências que vencem a partir dela viram lançamento sozinhas. Nulo = desligada (o padrão).
-- Só adiciona coluna anulável: a versão anterior do app continua funcionando com este schema.
alter table recorrencia add column confirmar_sozinha_desde date;
