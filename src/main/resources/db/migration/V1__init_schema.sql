-- 1. Bloques oficiales del examen y progreso de lectura de PDFs
CREATE TABLE syllabus_topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,          -- Ej: 'REST_SEARCH', 'PROFILING', 'FOUNDATIONS'
    name TEXT NOT NULL,
    exam_weight_percent REAL NOT NULL,  -- Peso oficial en el examen (%)
    study_status TEXT DEFAULT 'PENDING' -- PENDING, IN_PROGRESS, MASTERED
);

-- 2. Fragmentos extraídos de los PDFs del curso y doc oficial
CREATE TABLE study_materials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    topic_id INTEGER REFERENCES syllabus_topics(id),
    source_type TEXT NOT NULL,          -- 'COURSE_PDF', 'OFFICIAL_FHIR_SPEC', 'MANUAL'
    title TEXT NOT NULL,
    content_text TEXT NOT NULL,
    reference_url_or_page TEXT          -- Ej: 'Modulo_3.pdf#p14' o URL hl7.org/fhir
);

-- 3. Banco de preguntas (originales de prueba + generadas)
CREATE TABLE questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    topic_id INTEGER REFERENCES syllabus_topics(id),
    parent_question_id INTEGER REFERENCES questions(id), -- Si es variante de otra
    source TEXT NOT NULL,               -- 'MOCK_EXAM', 'AI_GENERATED', 'MANUAL'
    question_type TEXT NOT NULL,         -- 'SINGLE_CHOICE', 'MULTI_CHOICE', 'FHIR_URL'
    difficulty INTEGER DEFAULT 2,       -- 1 (Básico), 2 (Examen), 3 (Avanzado)
    stem TEXT NOT NULL,                 -- Enunciado de la pregunta
    code_snippet TEXT,                  -- JSON/XML FHIR o URL opcional
    explanation TEXT NOT NULL,          -- Explicación detallada y cita a la doc
    fhir_spec_url TEXT,                 -- Enlace directo al apartado de hl7.org/fhir/
    is_verified BOOLEAN DEFAULT 1
);

-- 4. Opciones de respuesta (con justificación individual de distractores)
CREATE TABLE question_options (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id INTEGER REFERENCES questions(id) ON DELETE CASCADE,
    option_label TEXT NOT NULL,         -- 'A', 'B', 'C', 'D'
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    distractor_reason TEXT              -- Por qué es falsa o verdadera esta opción
);

-- 5. Simulacros de examen e historial de respuestas por pregunta
CREATE TABLE exam_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mode TEXT NOT NULL,                 -- 'SIMULATION', 'ADAPTIVE', 'TUTOR', 'ERRORS'
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME,
    score_percent REAL
);

CREATE TABLE exam_attempt_answers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    attempt_id INTEGER REFERENCES exam_attempts(id) ON DELETE CASCADE,
    question_id INTEGER REFERENCES questions(id),
    selected_options TEXT NOT NULL,     -- Ej: 'B' o 'A,C'
    is_correct BOOLEAN NOT NULL,
    time_spent_seconds INTEGER,
    is_flagged BOOLEAN DEFAULT 0        -- Marcada con duda durante el examen
);

-- 6. Seguimiento de repetición espaciada (SM-2) por pregunta
CREATE TABLE question_srs_state (
    question_id INTEGER PRIMARY KEY REFERENCES questions(id),
    consecutive_correct INTEGER DEFAULT 0,
    easiness_factor REAL DEFAULT 2.5,
    interval_days INTEGER DEFAULT 1,
    next_review_date DATE
);

-- Índices para optimizar consultas de analíticas y exámenes adaptativos
CREATE INDEX idx_questions_topic ON questions(topic_id);
CREATE INDEX idx_attempt_answers_attempt ON exam_attempt_answers(attempt_id);
CREATE INDEX idx_attempt_answers_question ON exam_attempt_answers(question_id);
CREATE INDEX idx_srs_next_review ON question_srs_state(next_review_date);
