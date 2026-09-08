-- Required for case-insensitive email column
CREATE EXTENSION IF NOT EXISTS citext;

-- Shared Hibernate sequence.
-- The ERD uses:
--   @SequenceGenerator(name = "entity_seq", sequenceName = "entity_id_seq", allocationSize = 50)
--
-- Therefore the sequence must increment by 50.
CREATE SEQUENCE IF NOT EXISTS entity_id_seq
    START WITH 50
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;