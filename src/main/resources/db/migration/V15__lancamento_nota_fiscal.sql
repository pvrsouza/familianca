-- Nota fiscal das receitas tributáveis: de que mês é o serviço (competência) e quando a nota foi emitida. O
-- lançamento continua contando no mês da data (a do pagamento); os dois campos são só da nota e opcionais.
-- Reverter: alter table lancamento drop column nf_competencia, drop column nf_emissao;
alter table lancamento add column nf_competencia varchar(7)
    check (nf_competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$');
alter table lancamento add column nf_emissao date;
