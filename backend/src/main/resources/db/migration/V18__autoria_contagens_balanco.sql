-- O contador do item pode ser diferente do administrador que aplica os ajustes.
-- Não inferir a autoria de contagens antigas a partir do criador ou do aplicador.
alter table itens_balanco add column usuario_contagem_id bigint;
alter table itens_balanco add column usuario_contagem_nome varchar(120);
alter table itens_balanco add column contagem_em timestamp;
alter table itens_balanco add constraint fk_item_contador_tenant
    foreign key (restaurante_id, usuario_contagem_id) references usuarios(restaurante_id, id);
