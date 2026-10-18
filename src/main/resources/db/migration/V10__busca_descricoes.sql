-- Busca das descrições já usadas (sugestão ao lançar): unaccent ignora acentos e pg_trgm compara por trigramas,
-- achando o que se digita pela metade ou com erro ("mercdo" → "Mercado do mês"). As duas extensões vêm com o
-- Postgres e são "trusted" (PG 13+): o dono do banco (familianca_ddl) cria sem superusuário. Só adiciona funções:
-- a versão anterior do app continua funcionando.
create extension if not exists unaccent;
create extension if not exists pg_trgm;
