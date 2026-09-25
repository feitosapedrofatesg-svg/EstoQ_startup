-- Backfill: os dados existentes passam a pertencer ao restaurante "EstoQ Padrão"
-- e o administrador mais antigo torna-se o perfil PLATAFORMA (dono da plataforma,
-- restaurante_id nulo = enxerga todos os tenants).

insert into restaurantes (nome, ativo, data_hora_criacao, version)
values ('EstoQ Padrão', true, now(), 0);

update ajustes set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update alertas set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update auditoria set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update balancos set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update categorias set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update configuracoes_balanco set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update consumos set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update desperdicios set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update entradas set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update itens_balanco set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update lotes set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update movimentacoes set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update parametros_cmv set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update parametros_estoque set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update produtos set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update produtos_abertos set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;
update usuarios set restaurante_id = (select min(id) from restaurantes) where restaurante_id is null;

-- O CHECK antigo do perfil (criado por ddl-auto em versões anteriores) não inclui o
-- novo valor PLATAFORMA; remove-se qualquer CHECK que mencione a coluna perfil antes
-- do promote — o Hibernate recria o CHECK com a lista atual do enum no boot seguinte.
do $$
declare r record;
begin
    for r in
        select conname, oid
        from pg_constraint
        where conrelid = 'usuarios'::regclass
          and contype = 'c'
          and connamespace = (select oid from pg_namespace where nspname = current_schema())
    loop
        if pg_get_constraintdef(r.oid) like '%perfil%' then
            execute format('alter table usuarios drop constraint %I', r.conname);
        end if;
    end loop;
end $$;

update usuarios
set perfil = 'PLATAFORMA', restaurante_id = null
where id = (select min(id) from usuarios where perfil = 'ADMIN' and ativo = true);

-- Unicidade que era global passa a valer por restaurante (código de lote e parâmetro de produto).

do $$
declare r record;
begin
    for r in
        select conrelid::regclass::text as tabela, conname
        from pg_constraint
        where contype = 'u'
          and conrelid::regclass::text in ('lotes', 'parametros_estoque')
          and connamespace = (select oid from pg_namespace where nspname = current_schema())
    loop
        execute format('alter table %I drop constraint %I', r.tabela, r.conname);
    end loop;
end $$;

alter table lotes add constraint uk_lotes_tenant_codigo unique (restaurante_id, codigo);
alter table parametros_estoque add constraint uk_parametros_estoque_tenant_produto unique (restaurante_id, produto_id);