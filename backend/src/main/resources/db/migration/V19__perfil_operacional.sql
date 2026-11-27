-- Amplia somente o domínio de perfis, sem alterar usuários existentes.
do $$ declare c record; begin
  for c in select conname from pg_constraint where conrelid='usuarios'::regclass
    and contype='c' and pg_get_constraintdef(oid) like '%perfil%'
  loop execute format('alter table usuarios drop constraint %I', c.conname); end loop;
end $$;
alter table usuarios add constraint ck_usuario_perfil
  check (perfil in ('ADMIN','COZINHA','OPERACIONAL','NUTRICIONISTA','PLATAFORMA'));
