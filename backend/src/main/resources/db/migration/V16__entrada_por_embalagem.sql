-- Dados novos opcionais; não reconstrói embalagens ou saldos históricos.
alter table entradas add column quantidade_compra numeric(18,3);
alter table entradas add column conteudo_embalagem numeric(18,6);
alter table entradas add column unidade_conteudo varchar(20);
