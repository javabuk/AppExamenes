-- V3: Añadir columna file_path para almacenar la ruta al archivo binario original (PDFs)
ALTER TABLE study_materials ADD COLUMN file_path TEXT;
