-- ============================================================
-- Dados de demonstração (apenas dev, ou prod se FLYWAY_LOCATIONS incluir classpath:db/seed)
-- Senha de TODOS os usuários de demo: Atlas@123
-- Login do administrador: admin@atlaserp.com
-- ============================================================

INSERT INTO users (name, email, password_hash, role, status, created_at, updated_at, created_by) VALUES
  ('Admin Atlas',      'admin@atlaserp.com',          '!DISABLED!', 'ADMIN',    'ACTIVE',  now() - interval '200 days', now(), 'system'),
  ('Lucas Ferreira',   'lucas.ferreira@atlaserp.com', '!DISABLED!', 'ADMIN',    'ACTIVE',  now() - interval '9 days',   now(), 'system'),
  ('Mariana Silva',    'mariana.silva@atlaserp.com',  '!DISABLED!', 'MANAGER',  'ACTIVE',  now() - interval '11 days',  now(), 'system'),
  ('Rafael Costa',     'rafael.costa@atlaserp.com',   '!DISABLED!', 'EMPLOYEE', 'ACTIVE',  now() - interval '13 days',  now(), 'system'),
  ('Fernanda Almeida', 'fernanda.almeida@atlaserp.com','!DISABLED!','EMPLOYEE', 'ACTIVE',  now() - interval '16 days',  now(), 'system'),
  ('Gabriel Martins',  'gabriel.martins@atlaserp.com','!DISABLED!', 'VIEWER',   'PENDING', now() - interval '18 days',  now(), 'system'),
  ('Isabela Rocha',    'isabela.rocha@atlaserp.com',  '!DISABLED!', 'EMPLOYEE', 'ACTIVE',  now() - interval '24 days',  now(), 'system');

-- Usuários adicionais gerados de forma determinística, espalhados nos últimos ~6 meses
INSERT INTO users (name, email, password_hash, role, status, created_at, updated_at, created_by)
SELECT
    f.first_name || ' ' || l.last_name,
    lower(f.first_name || '.' || l.last_name || g.n) || '@atlaserp.com',
    '!DISABLED!',
    CASE
        WHEN g.n % 10 IN (0, 1) THEN 'MANAGER'
        WHEN g.n % 10 IN (2, 3) THEN 'VIEWER'
        WHEN g.n % 10 = 9       THEN 'GUEST'
        ELSE 'EMPLOYEE'
    END,
    CASE
        WHEN g.n % 9 = 0  THEN 'PENDING'
        WHEN g.n % 13 = 0 THEN 'INACTIVE'
        ELSE 'ACTIVE'
    END,
    now() - ((g.n * 3) % 170) * interval '1 day' - ((g.n * 37) % 1440) * interval '1 minute',
    now(),
    'system'
FROM generate_series(1, 60) AS g(n)
CROSS JOIN LATERAL (SELECT (ARRAY['Ana','Bruno','Carla','Diego','Eduarda','Felipe','Gustavo','Helena','Igor','Juliana',
                                  'Karina','Leonardo','Marcos','Natalia','Otavio','Patricia','Rodrigo','Sofia','Thiago','Vanessa'])[1 + (g.n * 7) % 20] AS first_name) f
CROSS JOIN LATERAL (SELECT (ARRAY['Santos','Oliveira','Souza','Lima','Pereira','Carvalho','Gomes','Ribeiro','Barbosa','Araujo',
                                  'Cardoso','Dias','Moreira','Nunes','Teixeira','Mendes','Freitas','Castro','Pinto','Monteiro'])[1 + (g.n * 11) % 20] AS last_name) l;
