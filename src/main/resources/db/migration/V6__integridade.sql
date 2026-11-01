-- Controle de concorrência: duas pessoas alterando o mesmo anúncio, ficha ou interessado ao
-- mesmo tempo não podem sobrescrever uma à outra (o segundo a gravar recebe um aviso).
ALTER TABLE anuncio ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ficha_tecnica ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE proposta ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

-- Um anúncio em andamento (não vendido) por veículo, garantido pelo banco. Índice único parcial
-- (WHERE status <> 'VENDIDO') não roda no H2, então a coluna só fica preenchida enquanto o anúncio
-- não foi vendido; vários NULL convivem numa coluna UNIQUE no PostgreSQL e no H2.
ALTER TABLE anuncio ADD COLUMN veiculo_em_andamento_id BIGINT;
ALTER TABLE anuncio ADD CONSTRAINT fk_anuncio_veiculo_em_andamento
    FOREIGN KEY (veiculo_em_andamento_id) REFERENCES veiculo (id);

-- Bases que já têm anúncios duplicados: só o mais recente de cada veículo fica marcado,
-- para a restrição abaixo não impedir a aplicação de subir.
UPDATE anuncio
SET veiculo_em_andamento_id = (SELECT f.veiculo_id FROM ficha_tecnica f WHERE f.id = anuncio.ficha_id)
WHERE status <> 'VENDIDO'
  AND id = (SELECT MAX(a2.id)
            FROM anuncio a2
                     JOIN ficha_tecnica f2 ON f2.id = a2.ficha_id
            WHERE a2.status <> 'VENDIDO'
              AND f2.veiculo_id = (SELECT f.veiculo_id FROM ficha_tecnica f WHERE f.id = anuncio.ficha_id));

ALTER TABLE anuncio ADD CONSTRAINT uk_anuncio_veiculo_em_andamento UNIQUE (veiculo_em_andamento_id);
