-- =============================================================================
-- V8: Soporte multi-certificación, syllabus dinámico y asignación a usuarios
-- =============================================================================

-- 1. Tabla de Certificaciones
CREATE TABLE IF NOT EXISTS certifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,                       -- Ej: 'FHIR_R4', 'FHIR_R5', 'HL7_V2'
    name TEXT NOT NULL,                             -- Ej: 'HL7 FHIR R4 Proficiency'
    description TEXT,
    standard_version TEXT NOT NULL DEFAULT 'R4',     -- 'R4', 'R5', 'V2', 'OTHER'
    exam_question_count INTEGER NOT NULL DEFAULT 50,
    exam_duration_minutes INTEGER NOT NULL DEFAULT 90,
    passing_score_percent REAL NOT NULL DEFAULT 70.0,
    spec_base_url TEXT DEFAULT 'https://hl7.org/fhir/R4/',
    feature_flags TEXT NOT NULL DEFAULT 'REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS',
    color TEXT DEFAULT '#3b82f6',
    icon TEXT DEFAULT '🧬',
    sort_order INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_certifications_code ON certifications(code);
CREATE INDEX IF NOT EXISTS idx_certifications_active ON certifications(active, sort_order);

-- Insertar certificación semilla FHIR_R4 (Activa por defecto)
INSERT INTO certifications (
    id, code, name, description, standard_version,
    exam_question_count, exam_duration_minutes, passing_score_percent,
    spec_base_url, feature_flags, color, icon, sort_order, active, created_at
) VALUES (
    1, 'FHIR_R4', 'HL7 FHIR R4 Proficiency Exam',
    'Certificación oficial HL7 FHIR Release 4 sobre fundamentos, intercambio RESTful, recursos clínicos, terminologías y profiling.',
    'R4', 50, 90, 70.0, 'https://hl7.org/fhir/R4/',
    'REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS',
    '#3b82f6', '🧬', 1, 1, datetime('now')
);

-- Insertar certificación semilla FHIR_R5 (Plantilla inactiva)
INSERT INTO certifications (
    id, code, name, description, standard_version,
    exam_question_count, exam_duration_minutes, passing_score_percent,
    spec_base_url, feature_flags, color, icon, sort_order, active, created_at
) VALUES (
    2, 'FHIR_R5', 'HL7 FHIR R5 Proficiency Exam (Borrador)',
    'Certificación oficial HL7 FHIR Release 5. Nuevos recursos, suscripciones topic-based y operaciones extendidas.',
    'R5', 50, 90, 70.0, 'https://hl7.org/fhir/R5/',
    'REST_SEARCH_BUILDER,RESOURCE_VALIDATOR,EXAM,FLASHCARDS,MATERIALS',
    '#8b5cf6', '🚀', 2, 0, datetime('now')
);

-- 2. Tabla de Áreas del Syllabus por Certificación (CertificationArea)
CREATE TABLE IF NOT EXISTS certification_areas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    certification_id INTEGER NOT NULL REFERENCES certifications(id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    weight_percent REAL NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 1,
    study_status TEXT DEFAULT 'PENDING',
    active BOOLEAN NOT NULL DEFAULT 1,
    UNIQUE(certification_id, code)
);

CREATE INDEX IF NOT EXISTS idx_cert_areas_cert ON certification_areas(certification_id, sort_order);

-- Migrar áreas de syllabus_topics a certification_areas asociadas a FHIR_R4 (id=1)
INSERT INTO certification_areas (id, certification_id, code, name, description, weight_percent, sort_order, study_status, active)
SELECT id, 1, code, name, name, exam_weight_percent, id, COALESCE(study_status, 'PENDING'), 1
FROM syllabus_topics;

-- Áreas iniciales para plantilla FHIR_R5
INSERT INTO certification_areas (certification_id, code, name, description, weight_percent, sort_order, study_status, active) VALUES
(2, 'FOUNDATIONS_R5', 'Fundamentos y Arquitectura FHIR R5', 'Principios básicos y arquitectura de FHIR R5', 15.0, 1, 'PENDING', 1),
(2, 'REST_SEARCH_R5', 'Intercambio RESTful y Búsqueda R5', 'Intercambio HTTP, nuevos parámetros y modificadores R5', 25.0, 2, 'PENDING', 1),
(2, 'RESOURCES_R5', 'Recursos Clínicos y Administrativos R5', 'Estructura y cambios en recursos clave en R5', 20.0, 3, 'PENDING', 1),
(2, 'TERMINOLOGY_R5', 'Terminologías y Servicios Terminológicos R5', 'CodeSystem, ValueSet y ConceptMap en R5', 15.0, 4, 'PENDING', 1),
(2, 'PROFILING_R5', 'Conformidad, StructureDefinition y Guías R5', 'Perfiles, extensiones y validación en R5', 15.0, 5, 'PENDING', 1),
(2, 'SECURITY_IMPL_R5', 'Seguridad, SMART on FHIR y Suscripciones R5', 'Seguridad, SMART v2 y Topic-based Subscriptions', 10.0, 6, 'PENDING', 1);

-- 3. Tabla de Asociación N:M Usuario <-> Certificación (UserCertification)
CREATE TABLE IF NOT EXISTS user_certifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    certification_id INTEGER NOT NULL REFERENCES certifications(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT 1,
    target_exam_date TEXT,
    enrolled_at TEXT NOT NULL,
    assigned_by TEXT,
    UNIQUE(user_id, certification_id)
);

CREATE INDEX IF NOT EXISTS idx_user_cert_user ON user_certifications(user_id, active);
CREATE INDEX IF NOT EXISTS idx_user_cert_cert ON user_certifications(certification_id);

-- Asignar automáticamente la certificación activa FHIR_R4 (id=1) a TODOS los usuarios existentes
INSERT INTO user_certifications (user_id, certification_id, active, enrolled_at, assigned_by)
SELECT id, 1, 1, datetime('now'), 'SYSTEM_MIGRATION_V8'
FROM users;

-- 4. Añadir certification_id y area_id a preguntas (questions)
ALTER TABLE questions ADD COLUMN certification_id INTEGER REFERENCES certifications(id);
ALTER TABLE questions ADD COLUMN area_id INTEGER REFERENCES certification_areas(id);
UPDATE questions SET certification_id = 1 WHERE certification_id IS NULL;
UPDATE questions SET area_id = topic_id WHERE area_id IS NULL AND topic_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_questions_cert ON questions(certification_id);
CREATE INDEX IF NOT EXISTS idx_questions_cert_area ON questions(certification_id, area_id);
CREATE INDEX IF NOT EXISTS idx_questions_cert_topic ON questions(certification_id, topic_id);

-- 5. Añadir certification_id y area_id a materiales de estudio (study_materials)
ALTER TABLE study_materials ADD COLUMN certification_id INTEGER REFERENCES certifications(id);
ALTER TABLE study_materials ADD COLUMN area_id INTEGER REFERENCES certification_areas(id);
UPDATE study_materials SET certification_id = 1 WHERE certification_id IS NULL;
UPDATE study_materials SET area_id = topic_id WHERE area_id IS NULL AND topic_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_materials_cert ON study_materials(certification_id);
CREATE INDEX IF NOT EXISTS idx_materials_cert_area ON study_materials(certification_id, area_id);

-- 6. Añadir certification_id a intentos de examen (exam_attempts)
ALTER TABLE exam_attempts ADD COLUMN certification_id INTEGER REFERENCES certifications(id);
UPDATE exam_attempts SET certification_id = 1 WHERE certification_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_attempts_cert ON exam_attempts(certification_id);
CREATE INDEX IF NOT EXISTS idx_attempts_user_cert ON exam_attempts(user_id, certification_id);

-- 7. Actualizar question_srs_state para asociar cada registro a la certificación
ALTER TABLE question_srs_state ADD COLUMN certification_id INTEGER REFERENCES certifications(id);
UPDATE question_srs_state SET certification_id = 1 WHERE certification_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_srs_user_cert ON question_srs_state(user_id, certification_id);
