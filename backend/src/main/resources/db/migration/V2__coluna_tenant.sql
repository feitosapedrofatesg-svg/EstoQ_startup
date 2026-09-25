-- Coluna de tenant nas 17 tabelas de negócio + FK para restaurantes.
-- Idempotente de propósito: implantações intermediárias com ddl-auto=update
-- podem já ter criado a coluna em parte das tabelas (ex.: incidente 2026-09-24).

alter table ajustes add column if not exists restaurante_id bigint;
alter table alertas add column if not exists restaurante_id bigint;
alter table auditoria add column if not exists restaurante_id bigint;
alter table balancos add column if not exists restaurante_id bigint;
alter table categorias add column if not exists restaurante_id bigint;
alter table configuracoes_balanco add column if not exists restaurante_id bigint;
alter table consumos add column if not exists restaurante_id bigint;
alter table desperdicios add column if not exists restaurante_id bigint;
alter table entradas add column if not exists restaurante_id bigint;
alter table itens_balanco add column if not exists restaurante_id bigint;
alter table lotes add column if not exists restaurante_id bigint;
alter table movimentacoes add column if not exists restaurante_id bigint;
alter table parametros_cmv add column if not exists restaurante_id bigint;
alter table parametros_estoque add column if not exists restaurante_id bigint;
alter table produtos add column if not exists restaurante_id bigint;
alter table produtos_abertos add column if not exists restaurante_id bigint;
alter table usuarios add column if not exists restaurante_id bigint;

-- PostgreSQL não tem "add constraint if not exists": criamos as FKs uma a uma,
-- apenas quando ainda não existem no schema corrente.

do $$
declare r record;
begin
    for r in
        select unnest(array[
            'ajustes', 'alertas', 'auditoria', 'balancos', 'categorias',
            'configuracoes_balanco', 'consumos', 'desperdicios', 'entradas',
            'itens_balanco', 'lotes', 'movimentacoes', 'parametros_cmv',
            'parametros_estoque', 'produtos', 'produtos_abertos', 'usuarios'
        ]) as tabela
    loop
        if not exists (
            select 1 from pg_constraint
            where conname = 'fk_' || r.tabela || '_restaurante'
              and connamespace = (select oid from pg_namespace where nspname = current_schema())
        ) then
            execute format(
                'alter table %I add constraint fk_%I_restaurante foreign key (restaurante_id) references restaurantes(id)',
                r.tabela, r.tabela);
        end if;
    end loop;
end $$;