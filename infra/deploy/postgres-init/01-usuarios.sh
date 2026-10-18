#!/bin/bash
# Roda só na criação do volume (banco vazio), pela imagem oficial do Postgres. Separa quem altera o schema de quem
# usa os dados (README, "Usuários do banco"):
#   familianca_ddl  dono das tabelas; só o Flyway usa (FLYWAY_USER).
#   familianca_app  a aplicação: lê e grava dados, sem criar/alterar tabelas (DB_USER).
# O superusuário (POSTGRES_USER) fica só para administração: psql, backup, restore.
set -euo pipefail

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v ddl_senha="$FLYWAY_PASSWORD" -v app_senha="$APP_PASSWORD" <<'SQL'
create role familianca_ddl login password :'ddl_senha';
create role familianca_app login password :'app_senha' nosuperuser nocreatedb nocreaterole;

alter database familianca owner to familianca_ddl;
alter schema public owner to familianca_ddl;
revoke create on schema public from public;
grant usage on schema public to familianca_app;

-- Vale para as tabelas e sequências que o familianca_ddl criar (todas as migrations).
alter default privileges for role familianca_ddl in schema public
    grant select, insert, update, delete on tables to familianca_app;
alter default privileges for role familianca_ddl in schema public
    grant usage, select on sequences to familianca_app;
SQL
