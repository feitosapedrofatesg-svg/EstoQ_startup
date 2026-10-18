-- Execute APÓS o primeiro boot com Flyway, no SQL Editor do projeto novo.
-- Gere o hash localmente com GerarHashSenha.java e substitua apenas o marcador.
-- Não colocar senha em claro, hash real ou conexão no Git ou na conversa.
-- Cria somente a conta global. Não concede PLATAFORMA a um usuário existente.
DO $$
DECLARE
    email_operador text := 'admin@estoq.com';
    nome_operador text := 'Pedro Feitosa';
    hash_operador text := 'COLE_O_HASH_BCRYPT_GERADO_LOCALMENTE';
BEGIN
    IF hash_operador !~ '^\$2[aby]\$1[0-6]\$[./A-Za-z0-9]{53}$' THEN
        RAISE EXCEPTION 'Substitua o marcador por um hash BCrypt local com custo entre 10 e 16.';
    END IF;
    IF EXISTS (SELECT 1 FROM estoq_v2.usuarios WHERE lower(email)=lower(email_operador)) THEN
        RAISE EXCEPTION 'Conta já existe. Nenhuma senha ou permissão foi alterada.';
    END IF;
    INSERT INTO estoq_v2.usuarios
        (nome,email,senha,perfil,data_hora_criacao,ativo,version,restaurante_id)
    VALUES (nome_operador,lower(email_operador),hash_operador,'PLATAFORMA',
        current_timestamp AT TIME ZONE 'UTC',true,0,NULL);
END
$$;
