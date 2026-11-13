-- Comentarios y notas adicionales por ficha dentro de colecciones de Fichas de Estudio (STUDY_CARDS)
CREATE TABLE IF NOT EXISTS study_card_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    material_id INTEGER NOT NULL REFERENCES study_materials(id) ON DELETE CASCADE,
    card_index INTEGER NOT NULL,
    card_heading TEXT,
    content_markdown TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_card_comments_material ON study_card_comments(material_id);
CREATE INDEX IF NOT EXISTS idx_card_comments_material_card ON study_card_comments(material_id, card_index);
