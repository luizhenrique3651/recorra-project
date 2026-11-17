-- Baseline do core-api (REC-003).
-- Convenção das tabelas futuras: PK uuid (gen_random_uuid()), criado_em/atualizado_em com default now().
-- Multi-tenant: índices começam por comercio_id (RLS e consultas sempre filtram por comércio).
-- A avaliar no card do modelo (ingestão/outbox): UUIDv7 e/ou PK composta (comercio_id, id).
-- Versão: V<sprint 2 dígitos><seq 2 dígitos>, pela ordem de execução; outOfOrder é proibido.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
