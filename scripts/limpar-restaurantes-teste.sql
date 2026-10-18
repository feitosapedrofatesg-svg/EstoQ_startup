-- Executado somente pelo utilitário administrativo, dentro de uma transação.
-- alvos_limpeza contém exclusivamente os IDs congelados durante a inspeção.
DO $$
DECLARE conhecidas text[] := ARRAY['itens_nota_fiscal','vinculos_fornecedor','notas_fiscais','operacoes_consumo','cmv_receitas_periodo','alertas','auditoria','auditoria_plataforma','ajustes','consumos','desperdicios','entradas','movimentacoes','itens_balanco','balancos','produtos_abertos','parametros_estoque','lotes','produtos','categorias','configuracoes_balanco','parametros_cmv','usuarios'];
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=current_schema() AND column_name='restaurante_id'
        AND NOT (table_name=ANY(conhecidas))) THEN
        RAISE EXCEPTION 'Há tabela de restaurante desconhecida. Revise o script antes de limpar.';
    END IF;
END $$;
-- Bloqueia escritas durante a validação e exclusão; leituras continuam disponíveis.
LOCK TABLE restaurantes, balanco_categorias, itens_nota_fiscal, vinculos_fornecedor, notas_fiscais, operacoes_consumo, cmv_receitas_periodo, alertas, auditoria, auditoria_plataforma, ajustes, consumos, desperdicios, entradas, movimentacoes, itens_balanco, balancos, produtos_abertos, parametros_estoque, lotes, produtos, categorias, configuracoes_balanco, parametros_cmv, usuarios IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE perfil='PLATAFORMA' AND ativo=true) THEN
        RAISE EXCEPTION 'Nenhuma conta PLATAFORMA ativa. Limpeza cancelada.';
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios WHERE perfil='PLATAFORMA'
        AND restaurante_id IN (SELECT id FROM alvos_limpeza)) THEN
        RAISE EXCEPTION 'Conta PLATAFORMA vinculada a restaurante selecionado. Limpeza cancelada.';
    END IF;
    IF EXISTS (SELECT 1 FROM alvos_limpeza a LEFT JOIN restaurantes r ON r.id=a.id
        WHERE r.id IS NULL OR r.nome IS DISTINCT FROM a.nome
            OR r.data_hora_criacao IS DISTINCT FROM a.criacao) THEN
        RAISE EXCEPTION 'Restaurantes divergem do manifesto. Faça nova inspeção.';
    END IF;
    IF EXISTS (SELECT 1 FROM ajustes filha JOIN movimentacoes mae ON mae.id=filha.id
        WHERE filha.restaurante_id IS NOT NULL AND
            (filha.restaurante_id IN (SELECT id FROM alvos_limpeza)) IS DISTINCT FROM
            (mae.restaurante_id IN (SELECT id FROM alvos_limpeza))) THEN
        RAISE EXCEPTION 'Tenant da tabela ajustes diverge da movimentação principal.';
    END IF;
    IF EXISTS (SELECT 1 FROM consumos filha JOIN movimentacoes mae ON mae.id=filha.id
        WHERE filha.restaurante_id IS NOT NULL AND
            (filha.restaurante_id IN (SELECT id FROM alvos_limpeza)) IS DISTINCT FROM
            (mae.restaurante_id IN (SELECT id FROM alvos_limpeza))) THEN
        RAISE EXCEPTION 'Tenant da tabela consumos diverge da movimentação principal.';
    END IF;
    IF EXISTS (SELECT 1 FROM desperdicios filha JOIN movimentacoes mae ON mae.id=filha.id
        WHERE filha.restaurante_id IS NOT NULL AND
            (filha.restaurante_id IN (SELECT id FROM alvos_limpeza)) IS DISTINCT FROM
            (mae.restaurante_id IN (SELECT id FROM alvos_limpeza))) THEN
        RAISE EXCEPTION 'Tenant da tabela desperdicios diverge da movimentação principal.';
    END IF;
    IF EXISTS (SELECT 1 FROM entradas filha JOIN movimentacoes mae ON mae.id=filha.id
        WHERE filha.restaurante_id IS NOT NULL AND
            (filha.restaurante_id IN (SELECT id FROM alvos_limpeza)) IS DISTINCT FROM
            (mae.restaurante_id IN (SELECT id FROM alvos_limpeza))) THEN
        RAISE EXCEPTION 'Tenant da tabela entradas diverge da movimentação principal.';
    END IF;
    IF EXISTS (SELECT 1 FROM balanco_categorias bc JOIN balancos b ON b.id=bc.balanco_id
        JOIN categorias c ON c.id=bc.categoria_id
        WHERE (b.restaurante_id IN (SELECT id FROM alvos_limpeza))
            IS DISTINCT FROM (c.restaurante_id IN (SELECT id FROM alvos_limpeza))) THEN
        RAISE EXCEPTION 'Categorias e balanços cruzam restaurantes selecionados e preservados.';
    END IF;
END $$;
UPDATE restaurantes SET ativo=false WHERE id IN (SELECT id FROM alvos_limpeza);
-- A junção não tem restaurante_id; é removida antes das entidades referenciadas.
DELETE FROM balanco_categorias WHERE balanco_id IN
    (SELECT id FROM balancos WHERE restaurante_id IN (SELECT id FROM alvos_limpeza));
-- A ordem respeita as chaves estrangeiras, sem CASCADE nem desativar restrições.
DELETE FROM itens_nota_fiscal WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM vinculos_fornecedor WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM notas_fiscais WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM operacoes_consumo WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM cmv_receitas_periodo WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM alertas WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM auditoria_plataforma WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM auditoria WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
-- Herança JOINED: dados antigos podem ter tenant somente na movimentação mãe.
DELETE FROM ajustes WHERE id IN
    (SELECT id FROM movimentacoes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza));
-- Herança JOINED: dados antigos podem ter tenant somente na movimentação mãe.
DELETE FROM consumos WHERE id IN
    (SELECT id FROM movimentacoes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza));
-- Herança JOINED: dados antigos podem ter tenant somente na movimentação mãe.
DELETE FROM desperdicios WHERE id IN
    (SELECT id FROM movimentacoes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza));
-- Herança JOINED: dados antigos podem ter tenant somente na movimentação mãe.
DELETE FROM entradas WHERE id IN
    (SELECT id FROM movimentacoes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza));
DELETE FROM movimentacoes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM itens_balanco WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM balancos WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM produtos_abertos WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM parametros_estoque WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM lotes WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM produtos WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM categorias WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM configuracoes_balanco WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM parametros_cmv WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM usuarios WHERE restaurante_id IN (SELECT id FROM alvos_limpeza);
DELETE FROM restaurantes WHERE id IN (SELECT id FROM alvos_limpeza);
-- Sequências, schema, migrações e contas da plataforma permanecem intactos.
