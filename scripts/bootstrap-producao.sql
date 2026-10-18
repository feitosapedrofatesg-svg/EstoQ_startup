-- Provisionamento manual da PLATAFORMA. Execute com psql em uma sessão administrativa.
-- Gere um hash BCrypt (custo 12) para uma senha única em ferramenta local confiável.
-- O script não cria restaurante, parâmetros ou usuário de demonstração.
-- Não use este arquivo para conceder PLATAFORMA a um ADMIN de restaurante.
\set ON_ERROR_STOP on
\prompt 'E-mail da plataforma: ' plataforma_email
\prompt 'Nome do operador: ' plataforma_nome
\prompt 'Hash BCrypt da senha única: ' plataforma_hash

select length(trim(:'plataforma_email')) between 3 and 254
   and length(trim(:'plataforma_nome')) between 1 and 120
   and :'plataforma_hash' ~ '^\$2[aby]\$1[0-6]\$[./A-Za-z0-9]{53}$' as dados_validos \gset
\if :dados_validos
\else
    \echo 'Dados inválidos. Use e-mail, nome e hash BCrypt com custo entre 10 e 16.'
    \quit 2
\endif

begin;
insert into estoq_v2.usuarios (nome, email, senha, perfil, data_hora_criacao, ativo, version, restaurante_id)
values (trim(:'plataforma_nome'), lower(trim(:'plataforma_email')), :'plataforma_hash',
        'PLATAFORMA', (current_timestamp at time zone 'UTC'), true, 0, null)
on conflict (email) do nothing;

-- A conta existente deve já ser da plataforma; a senha nova revoga sessões pela validação do hash.
update estoq_v2.usuarios
set senha=:'plataforma_hash', version=version+1
where email=lower(trim(:'plataforma_email')) and perfil='PLATAFORMA' and restaurante_id is null and ativo=true;
\if :ROW_COUNT
    commit;
    \echo 'Conta da plataforma provisionada. Não guarde a senha neste repositório.'
\else
    rollback;
    \echo 'Operação recusada: a conta existente não é uma PLATAFORMA ativa.'
    \quit 2
\endif
\unset plataforma_hash
