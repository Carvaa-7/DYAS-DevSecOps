-- Esquema de la base de datos. Se ejecuta en cada arranque (spring.sql.init.mode=always),
-- por eso todas las sentencias usan IF NOT EXISTS.
CREATE TABLE IF NOT EXISTS reservas (
    id             UUID         PRIMARY KEY,
    sala           VARCHAR(10)  NOT NULL,
    responsable    VARCHAR(120) NOT NULL,
    fecha          DATE         NOT NULL,
    inicio         TIME         NOT NULL,
    fin            TIME         NOT NULL,
    registrada_por VARCHAR(60)  NOT NULL,
    estado         VARCHAR(10)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reservas_sala_fecha ON reservas (sala, fecha);
CREATE INDEX IF NOT EXISTS idx_reservas_fecha ON reservas (fecha);
