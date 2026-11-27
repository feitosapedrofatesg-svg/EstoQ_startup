alter table usuarios add column tema varchar(20) not null default 'claro';
alter table usuarios add column fonte integer not null default 100;
alter table usuarios add column idioma varchar(10) not null default 'pt-BR';
alter table usuarios add constraint ck_preferencias_usuario check
    (tema in ('claro','escuro','original','terroso','roxo','verde','azul') and fonte between 90 and 125 and idioma in ('pt-BR','es','en'));
