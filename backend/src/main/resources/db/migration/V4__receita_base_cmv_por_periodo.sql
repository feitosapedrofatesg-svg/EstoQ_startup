create table cmv_receitas_periodo (
    id bigserial primary key,
    data_hora_criacao timestamp not null,
    ativo boolean not null default true,
    version bigint not null default 0,
    restaurante_id bigint references restaurantes(id),
    data_inicio date not null,
    data_fim date not null,
    receita_base numeric(18, 2),
    constraint uk_cmv_receita_tenant_periodo unique (restaurante_id, data_inicio, data_fim)
);