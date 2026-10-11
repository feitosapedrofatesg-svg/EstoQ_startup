-- Só restaurantes ativos sem categorias ativas recebem a lista inicial.
-- A organização e as categorias inativas existentes permanecem preservadas.
with restaurantes_vazios as (
    select r.id from restaurantes r
    where r.ativo = true
      and not exists (select 1 from categorias c where c.restaurante_id = r.id and c.ativo = true)
), categorias_padrao(nome) as (
    values
        ('Carnes'),
        ('Peixes e frutos do mar'),
        ('Laticínios'),
        ('Grãos'),
        ('Hortifruti'),
        ('Massas'),
        ('Temperos e condimentos'),
        ('Óleos e molhos'),
        ('Mercearia'),
        ('Bebidas'),
        ('Limpeza'),
        ('Descartáveis e embalagens'),
        ('Sem classificação')
)
insert into categorias (restaurante_id, nome, descricao, ativo, data_hora_criacao, version)
select r.id, c.nome, 'Categoria padrão do restaurante.', true, current_timestamp, 0
from restaurantes_vazios r cross join categorias_padrao c;
