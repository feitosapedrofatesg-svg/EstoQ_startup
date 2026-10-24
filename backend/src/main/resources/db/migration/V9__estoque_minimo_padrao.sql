-- Novos registros também recebem os níveis iniciais quando criados por SQL.
alter table parametros_estoque alter column estoque_minimo set default 3;
alter table parametros_estoque alter column estoque_medio set default 3;
alter table parametros_estoque alter column estoque_maximo set default 3;

-- Atualização única dos mínimos zerados, autorizada para os produtos existentes.
-- Preserva mínimos personalizados e níveis médio/máximo que já sejam maiores.
update parametros_estoque
set estoque_minimo = 3,
    estoque_medio = greatest(estoque_medio, 3),
    estoque_maximo = greatest(estoque_maximo, estoque_medio, 3),
    version = version + 1,
    data_atualizacao = current_timestamp at time zone 'UTC'
where estoque_minimo = 0;
