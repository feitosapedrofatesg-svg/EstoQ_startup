ALTER TABLE usuarios ADD COLUMN google_sub VARCHAR(255);
CREATE UNIQUE INDEX uk_usuario_google_sub ON usuarios (google_sub);
ALTER TABLE usuarios ALTER COLUMN senha DROP NOT NULL;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_credencial
    CHECK (senha IS NOT NULL OR google_sub IS NOT NULL);
