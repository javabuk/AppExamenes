-- =============================================================================
-- V7: Autenticación multiusuario, auditoría y propiedad/trazabilidad de datos
-- =============================================================================

-- 1. Tabla de Usuarios
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT 1,
    must_change_password BOOLEAN NOT NULL DEFAULT 1,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TEXT,
    last_login_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT,
    created_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role_active ON users(role, active);

-- Insertar usuario administrador inicial (bootstrap).
-- Su contraseña será inicializada en el arranque por AdminBootstrapInitializer
-- a partir de APP_ADMIN_PASSWORD o generada aleatoriamente.
INSERT INTO users (
    id, username, email, display_name, password_hash, role,
    active, must_change_password, failed_login_attempts, created_at, created_by
) VALUES (
    1, 'admin', 'admin@fhir.local', 'Administrador Principal', '!BOOTSTRAP_PENDING!', 'ADMIN',
    1, 1, 0, datetime('now'), 'SYSTEM_MIGRATION'
);

-- 2. Tabla de Auditoría de Seguridad y Administración
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    actor_user_id INTEGER REFERENCES users(id),
    actor_username TEXT NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT,
    target_id TEXT,
    details TEXT,
    ip_address TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);

-- 3. Recrear question_srs_state para soportar estado SM-2 por (user_id, question_id)
CREATE TABLE question_srs_state_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    consecutive_correct INTEGER DEFAULT 0,
    easiness_factor REAL DEFAULT 2.5,
    interval_days INTEGER DEFAULT 1,
    next_review_date DATE,
    updated_at TEXT,
    UNIQUE(user_id, question_id)
);

INSERT INTO question_srs_state_new (
    user_id, question_id, consecutive_correct, easiness_factor, interval_days, next_review_date, updated_at
)
SELECT 1, question_id, consecutive_correct, easiness_factor, interval_days, next_review_date, datetime('now')
FROM question_srs_state;

DROP TABLE question_srs_state;
ALTER TABLE question_srs_state_new RENAME TO question_srs_state;

CREATE INDEX IF NOT EXISTS idx_srs_user_next_review ON question_srs_state(user_id, next_review_date);
CREATE INDEX IF NOT EXISTS idx_srs_user_question ON question_srs_state(user_id, question_id);

-- 4. Asociar intentos de examen (exam_attempts) al usuario
ALTER TABLE exam_attempts ADD COLUMN user_id INTEGER REFERENCES users(id);
UPDATE exam_attempts SET user_id = 1 WHERE user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_exam_attempts_user ON exam_attempts(user_id);
CREATE INDEX IF NOT EXISTS idx_exam_attempts_user_finished ON exam_attempts(user_id, finished_at);

-- 5. Asociar comentarios y notas de fichas (study_card_comments) al usuario
ALTER TABLE study_card_comments ADD COLUMN user_id INTEGER REFERENCES users(id);
UPDATE study_card_comments SET user_id = 1 WHERE user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_card_comments_user_material ON study_card_comments(user_id, material_id);

-- 6. Añadir propietario, auditoría y borrado lógico (soft delete) a questions
ALTER TABLE questions ADD COLUMN owner_id INTEGER REFERENCES users(id);
ALTER TABLE questions ADD COLUMN created_at TEXT;
ALTER TABLE questions ADD COLUMN updated_at TEXT;
ALTER TABLE questions ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE questions ADD COLUMN deleted_at TEXT;

UPDATE questions SET owner_id = 1, created_at = datetime('now') WHERE owner_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_questions_owner ON questions(owner_id);
CREATE INDEX IF NOT EXISTS idx_questions_deleted ON questions(deleted);

-- 7. Añadir propietario, auditoría y borrado lógico (soft delete) a study_materials
ALTER TABLE study_materials ADD COLUMN owner_id INTEGER REFERENCES users(id);
ALTER TABLE study_materials ADD COLUMN created_at TEXT;
ALTER TABLE study_materials ADD COLUMN updated_at TEXT;
ALTER TABLE study_materials ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE study_materials ADD COLUMN deleted_at TEXT;

UPDATE study_materials SET owner_id = 1, created_at = datetime('now') WHERE owner_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_materials_owner ON study_materials(owner_id);
CREATE INDEX IF NOT EXISTS idx_materials_deleted ON study_materials(deleted);
