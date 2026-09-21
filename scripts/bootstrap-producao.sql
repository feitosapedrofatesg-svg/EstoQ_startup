-- ============================================================
-- Bootstrap de produção — EstoQ
-- ============================================================
-- Execute UMA VEZ no SQL editor do Neon APÓS o primeiro boot do
-- backend (quando as tabelas já existirem no schema estoq_v2).
--
-- Cria o usuário ADMIN inicial + parâmetros de configuração
-- (o seed é exclusivo do perfil dev e NÃO roda em produção).
--
-- Troque o hash abaixo se quiser outra senha. Hash atual:
-- BCrypt custo 10 para a senha  Admin@12345
-- ============================================================

INSERT INTO estoq_v2.usuarios (nome, email, senha, perfil, data_hora_criacao, ativo, version)
SELECT 'Admin EstoQ', 'admin@estoq.com', '$2y$10$9vpyeuYJmd2FABCv5K7ZuOuctdQwEhRKDLO7qP0u2Rkf0zCp1qnmS', 'ADMIN', now(), true, 0
WHERE NOT EXISTS (SELECT 1 FROM estoq_v2.usuarios WHERE email = 'admin@estoq.com');

INSERT INTO estoq_v2.parametros_cmv (percentual_ideal, data_atualizacao, data_hora_criacao, ativo, version)
SELECT 30.00, now(), now(), true, 0
WHERE NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_cmv WHERE ativo = true);

INSERT INTO estoq_v2.configuracoes_balanco (periodicidade, dia_execucao, proxima_execucao, data_hora_criacao, ativo, version)
SELECT 'MENSAL', 1, date_trunc('month', now()) + interval '1 month', now(), true, 0
WHERE NOT EXISTS (SELECT 1 FROM estoq_v2.configuracoes_balanco WHERE ativo = true);