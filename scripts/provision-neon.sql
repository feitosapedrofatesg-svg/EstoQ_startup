-- ============================================================
-- Script de provisionamento para Neon / Postgres managed
-- ============================================================
-- Execute este script UMA VEZ no editor SQL do Neon Dashboard
-- antes do primeiro deploy do backend.
--
-- O que faz:
--   1. Cria o schema estoq_v2 (o app usa currentSchema=estoq_v2)
--   2. Concede permissões ao usuário dono do banco
--
-- Depois de rodar este script, defina no Render:
--   DDL_AUTO=update  (primeiro boot cria as tabelas via Hibernate)
--   Após o primeiro boot bem-sucedido, mude para DDL_AUTO=validate
-- ============================================================

-- 1. Criar schema (se não existir)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_namespace WHERE nspname = 'estoq_v2'
  ) THEN
    CREATE SCHEMA estoq_v2;
  END IF;
END
$$;

-- 2. Conceder permissões ao usuário do Neon
--    (ajuste o nome do usuário conforme sua string de conexão)
DO $$
DECLARE
  neon_user TEXT := current_user;
BEGIN
  EXECUTE format('GRANT ALL ON SCHEMA estoq_v2 TO %I', neon_user);
  EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA estoq_v2 GRANT ALL ON TABLES TO %I', neon_user);
  EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA estoq_v2 GRANT ALL ON SEQUENCES TO %I', neon_user);
END
$$;

-- 3. (Opcional) Grante para roles de leitura se criar usuários dedicados
-- GRANT USAGE ON SCHEMA estoq_v2 TO estoq_readonly;
-- GRANT SELECT ON ALL TABLES IN SCHEMA estoq_v2 TO estoq_readonly;
