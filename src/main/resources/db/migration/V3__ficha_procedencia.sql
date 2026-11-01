-- Novos campos da ficha: procedência (pública na loja) e documentação/débitos/proprietários (só equipe).
-- Toda ficha nasce com todos os campos do catálogo; as fichas já existentes recebem os novos como ausentes.
INSERT INTO campo_ficha (ficha_id, campo, status_verificacao)
SELECT f.id, c.campo, 'AUSENTE'
FROM ficha_tecnica f
CROSS JOIN (VALUES ('QUANTIDADE_DONOS'), ('ROUBO_FURTO'), ('LEILAO'), ('SINISTRO'), ('RESTRICOES'), ('RECALL'),
                   ('RENAVAM'), ('MUNICIPIO_REGISTRO'), ('PROPRIETARIO_ATUAL'), ('PROPRIETARIOS_ANTERIORES'),
                   ('MULTAS'), ('DEBITOS')) AS c (campo)
WHERE NOT EXISTS (SELECT 1 FROM campo_ficha cf WHERE cf.ficha_id = f.id AND cf.campo = c.campo);
