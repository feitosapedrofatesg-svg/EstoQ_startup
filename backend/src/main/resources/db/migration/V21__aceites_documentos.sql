-- Sem backfill: restaurantes anteriores não recebem confirmações inventadas.
create table aceites_documentos (
  id bigserial primary key, restaurante_id bigint not null references restaurantes(id),
  data_hora_criacao timestamp not null, version bigint not null default 0, ativo boolean not null default true,
  responsavel_id bigint not null, responsavel_nome varchar(120) not null,
  documento varchar(30) not null, versao varchar(60) not null, confirmado_em timestamp not null,
  constraint uk_aceite_cadastro unique(restaurante_id,documento),
  foreign key(restaurante_id,responsavel_id) references usuarios(restaurante_id,id)
);
