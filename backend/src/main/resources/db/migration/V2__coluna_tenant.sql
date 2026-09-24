-- Coluna de isolamento (tenant) em todas as tabelas de negócio.
-- Fica anulável de propósito: o perfil PLATAFORMA usa restaurante_id nulo e o valor
-- se preenche automaticamente no insert (via @TenantId do Hibernate).

alter table ajustes add column restaurante_id bigint;
alter table alertas add column restaurante_id bigint;
alter table auditoria add column restaurante_id bigint;
alter table balancos add column restaurante_id bigint;
alter table categorias add column restaurante_id bigint;
alter table configuracoes_balanco add column restaurante_id bigint;
alter table consumos add column restaurante_id bigint;
alter table desperdicios add column restaurante_id bigint;
alter table entradas add column restaurante_id bigint;
alter table itens_balanco add column restaurante_id bigint;
alter table lotes add column restaurante_id bigint;
alter table movimentacoes add column restaurante_id bigint;
alter table parametros_cmv add column restaurante_id bigint;
alter table parametros_estoque add column restaurante_id bigint;
alter table produtos add column restaurante_id bigint;
alter table produtos_abertos add column restaurante_id bigint;
alter table usuarios add column restaurante_id bigint;

alter table ajustes add constraint fk_ajustes_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table alertas add constraint fk_alertas_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table auditoria add constraint fk_auditoria_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table balancos add constraint fk_balancos_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table categorias add constraint fk_categorias_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table configuracoes_balanco add constraint fk_configuracao_balanco_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table consumos add constraint fk_consumos_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table desperdicios add constraint fk_desperdicios_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table entradas add constraint fk_entradas_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table itens_balanco add constraint fk_itens_balanco_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table lotes add constraint fk_lotes_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table movimentacoes add constraint fk_movimentacoes_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table parametros_cmv add constraint fk_parametros_cmv_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table parametros_estoque add constraint fk_parametros_estoque_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table produtos add constraint fk_produtos_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table produtos_abertos add constraint fk_produtos_abertos_restaurante foreign key (restaurante_id) references restaurantes(id);
alter table usuarios add constraint fk_usuarios_restaurante foreign key (restaurante_id) references restaurantes(id);