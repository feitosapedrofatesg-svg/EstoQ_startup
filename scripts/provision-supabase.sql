-- Execute no SQL Editor de um projeto Supabase NOVO, como postgres.
-- Não altera public, auth ou storage, nem importa dados do Neon.
-- Desative também a Data API nas configurações do projeto.
BEGIN;
CREATE SCHEMA IF NOT EXISTS estoq_v2 AUTHORIZATION postgres;
REVOKE ALL ON SCHEMA estoq_v2 FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON ALL TABLES IN SCHEMA estoq_v2 FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA estoq_v2 FROM PUBLIC, anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA estoq_v2
    REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA estoq_v2
    REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA estoq_v2
    REVOKE ALL ON FUNCTIONS FROM PUBLIC, anon, authenticated, service_role;
COMMIT;
-- O Flyway cria as tabelas no primeiro boot usando currentSchema=estoq_v2.
-- Não usar ddl-auto=update e não executar migrações antigas manualmente.
