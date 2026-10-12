-- A confirmação e sua resposta são persistidas na mesma transação da baixa.
create table operacoes_consumo (
    id bigserial primary key,
    data_hora_criacao timestamp not null,
    ativo boolean not null default true,
    version bigint not null default 0,
    restaurante_id bigint not null references restaurantes(id),
    chave varchar(36) not null,
    pedido text not null,
    resposta text not null,
    constraint uk_operacao_consumo unique (restaurante_id, chave)
);
