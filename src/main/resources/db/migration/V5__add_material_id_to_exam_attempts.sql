-- V5: Añadir columna material_id a exam_attempts para asociar intentos de práctica a documentos concretos
ALTER TABLE exam_attempts ADD COLUMN material_id INTEGER REFERENCES study_materials(id);

CREATE INDEX idx_exam_attempts_material ON exam_attempts(material_id);
