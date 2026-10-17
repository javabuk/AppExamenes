-- V4: Añadir columna material_id a questions para vincular preguntas a apuntes o documentos concretos
ALTER TABLE questions ADD COLUMN material_id INTEGER REFERENCES study_materials(id);

CREATE INDEX idx_questions_material ON questions(material_id);

-- Vincular preguntas iniciales a los materiales de estudio correspondientes:
-- Material 1: 'Especificación de Búsqueda RESTful FHIR' (id: 1) -> Preguntas 1, 2, 8
UPDATE questions SET material_id = 1 WHERE id IN (1, 2, 8);

-- Material 2: 'Tipos de Datos Primitivos y Complejos' (id: 2) -> Preguntas 4, 10
UPDATE questions SET material_id = 2 WHERE id IN (4, 10);

-- Material 3: 'Reglas de Extensiones y ModifierExtension' (id: 3) -> Preguntas 3, 9
UPDATE questions SET material_id = 3 WHERE id IN (3, 9);
