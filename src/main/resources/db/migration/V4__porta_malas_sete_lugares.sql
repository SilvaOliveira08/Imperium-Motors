-- Carros de 7 lugares ou mais: tamanho do porta-malas com a terceira fileira de bancos em uso.
INSERT INTO campo_ficha (ficha_id, campo, status_verificacao)
SELECT f.id, 'PORTA_MALAS_7_LUGARES', 'AUSENTE'
FROM ficha_tecnica f
WHERE NOT EXISTS (SELECT 1 FROM campo_ficha cf WHERE cf.ficha_id = f.id AND cf.campo = 'PORTA_MALAS_7_LUGARES');
