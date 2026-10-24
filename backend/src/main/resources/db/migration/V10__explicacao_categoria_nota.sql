ALTER TABLE itens_nota_fiscal ADD COLUMN origem_categoria VARCHAR(30);
ALTER TABLE itens_nota_fiscal ADD COLUMN motivo_categoria VARCHAR(500);
ALTER TABLE itens_nota_fiscal ADD COLUMN revisar_categoria BOOLEAN NOT NULL DEFAULT FALSE;
