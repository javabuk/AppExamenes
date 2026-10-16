-- Insertar los 6 bloques oficiales del examen de certificación HL7 FHIR
INSERT INTO syllabus_topics (id, code, name, exam_weight_percent, study_status) VALUES
(1, 'FOUNDATIONS', 'Fundamentos, Arquitectura y Tipos de Datos FHIR', 15.0, 'IN_PROGRESS'),
(2, 'REST_SEARCH', 'Intercambio RESTful, Verbos HTTP y Búsqueda Avanzada', 25.0, 'IN_PROGRESS'),
(3, 'RESOURCES', 'Recursos Clínicos, Administrativos y Referencias', 20.0, 'IN_PROGRESS'),
(4, 'TERMINOLOGY', 'Terminologías, CodeSystem, ValueSet y ConceptMap', 15.0, 'PENDING'),
(5, 'PROFILING', 'Conformidad, StructureDefinition, Extensiones e IGs', 15.0, 'PENDING'),
(6, 'SECURITY_IMPL', 'Seguridad, SMART on FHIR, Bundles y Transacciones', 10.0, 'PENDING');

-- Materiales de estudio iniciales
INSERT INTO study_materials (topic_id, source_type, title, content_text, reference_url_or_page) VALUES
(2, 'OFFICIAL_FHIR_SPEC', 'Especificación de Búsqueda RESTful FHIR', 
 'Una búsqueda FHIR se ejecuta mediante GET [base]/[type]?[parameters] o POST [base]/[type]/_search. Los modificadores incluyen :exact, :contains, :missing, :text, :not, :in, :not-in, :below, :above. Parámetros de control estándar incluyen _include, _revinclude, _sort, _count, _summary y _elements.', 
 'https://hl7.org/fhir/R4/search.html'),

(1, 'OFFICIAL_FHIR_SPEC', 'Tipos de Datos Primitivos y Complejos', 
 'Todos los tipos de datos primitivos en FHIR tienen un valor (value) y pueden tener extensiones mediante un id y extension. Los tipos complejos comunes son CodeableConcept, Coding, Identifier, Reference, Period, Quantity y HumanName. Un CodeableConcept puede contener múltiples Coding que representan el mismo concepto en diferentes vocabularios.', 
 'https://hl7.org/fhir/R4/datatypes.html'),

(5, 'OFFICIAL_FHIR_SPEC', 'Reglas de Extensiones y ModifierExtension', 
 'Las extensiones normales añaden información adicional sin alterar el significado del recurso. Un modifierExtension altera la interpretación clínica o legal del elemento que lo contiene (por ejemplo, "no tiene alergia" o "condición descartada"). Cualquier sistema que no comprenda un modifierExtension DEBE rechazar el recurso.', 
 'https://hl7.org/fhir/R4/extensibility.html');

-- Pregunta 1: REST_SEARCH - Búsqueda de Observation y _include
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(1, 2, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'Un desarrollador necesita recuperar todas las observaciones de tipo glucosa en sangre (código LOINC 15074-8) con estado final para un paciente, e incluir los datos del paciente en una única llamada REST. ¿Cuál es la sintaxis correcta de la petición HTTP?',
'GET [base]/Observation?code=http://loinc.org|15074-8&status=final&subject=Patient/123&_include=Observation:subject',
'En FHIR, la inclusión de recursos referenciados se realiza mediante el parámetro _include=[RecursoOrigen]:[elementoReferencia]. En Observation, el enlace al paciente se encuentra en el elemento "subject", por lo que se utiliza "_include=Observation:subject".',
'https://hl7.org/fhir/R4/search.html#include', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(1, 'A', 'GET [base]/Observation?code=15074-8&status=final&patient=123&_join=Patient', 0, 'El parámetro estándar es _include, no existe _join en FHIR REST.'),
(1, 'B', 'GET [base]/Observation?code=http://loinc.org|15074-8&status=final&subject=Patient/123&_include=Observation:subject', 1, 'Correcto: utiliza el sistema|código, estado exacto, referencia de sujeto y _include=Observation:subject.'),
(1, 'C', 'GET [base]/Observation?code=http://loinc.org:15074-8&status=final&_revinclude=Patient:observation', 0, 'El separador sistema/código es una barra vertical "|", no dos puntos. Además _revinclude invierte la relación.'),
(1, 'D', 'GET [base]/Patient/123/Observation?code=15074-8&_expand=true', 0, 'FHIR no define un parámetro _expand para búsquedas de recursos.');

-- Pregunta 2: REST_SEARCH - Condicional Create (POST con If-None-Exist)
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(2, 2, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'Se desea registrar un recurso Patient sólo si no existe previamente otro paciente con el mismo número de identificación nacional (sistema: urn:oid:2.16.840.1.113883.4.1, valor: 987654321). ¿Qué cabecera HTTP y método deben utilizarse en FHIR R4?',
'POST [base]/Patient HTTP/1.1
If-None-Exist: identifier=urn:oid:2.16.840.1.113883.4.1|987654321',
'El estándar FHIR define el Conditional Create mediante una petición POST a la raíz del tipo de recurso acompañada de la cabecera "If-None-Exist" que contiene una cadena de búsqueda FHIR. Si coincide exactamente un recurso, el servidor retorna 200 OK sin duplicar.',
'https://hl7.org/fhir/R4/http.html#ccreate', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(2, 'A', 'PUT [base]/Patient con cabecera If-Match: identifier=...', 0, 'If-Match se utiliza para control de concurrencia y versionado (ETags), no para creación condicional.'),
(2, 'B', 'POST [base]/Patient con cabecera If-None-Exist: identifier=urn:oid:2.16.840.1.113883.4.1|987654321', 1, 'Correcto: POST a la ruta del recurso con cabecera If-None-Exist especifica la búsqueda condicional.'),
(2, 'C', 'POST [base]/Patient?conditional=true&identifier=...', 0, 'La condición se envía en la cabecera HTTP If-None-Exist, no como parámetro de query string.'),
(2, 'D', 'PATCH [base]/Patient con cabecera If-None-Match: *', 0, 'PATCH se utiliza para actualización parcial, no para creación condicional basada en identificadores.');

-- Pregunta 3: PROFILING - modifierExtension vs extension
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(3, 5, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'¿Cuál es la diferencia crítica entre un elemento "extension" y un elemento "modifierExtension" en una instancia de recurso FHIR?',
'{
  "resourceType": "Condition",
  "modifierExtension": [{
    "url": "http://example.org/fhir/StructureDefinition/condition-refuted",
    "valueBoolean": true
  }]
}',
'Un modifierExtension altera el significado fundamental del recurso o elemento contenedor (por ejemplo, invirtiendo la condición a "descartada/refutada"). Una aplicación que procesa el recurso DEBE entender la extensión modificadora o, de lo contrario, rechazar el procesamiento del recurso con un error.',
'https://hl7.org/fhir/R4/extensibility.html#modifierExtension', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(3, 'A', 'No hay diferencia funcional; modifierExtension sólo se utiliza para extensiones complejas con subelementos.', 0, 'Falso: ambas pueden ser simples o complejas.'),
(3, 'B', 'modifierExtension altera el significado del recurso; si el consumidor no la reconoce, DEBE fallar el procesamiento.', 1, 'Correcto: según la especificación HL7 FHIR, un modifierExtension no reconocido obliga al receptor a rechazar el recurso.'),
(3, 'C', 'Las extensiones sólo se aplican a recursos administrativos y modifierExtension a recursos clínicos.', 0, 'Falso: ambas pueden utilizarse en cualquier recurso o tipo de datos en FHIR.'),
(3, 'D', 'modifierExtension sólo puede ser definida y publicada por el comité central de HL7 Internacional.', 0, 'Falso: cualquier implementador u organización puede definir modifierExtensions en sus Implementation Guides.');

-- Pregunta 4: FOUNDATIONS - CodeableConcept vs Coding
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(4, 1, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 1,
'En el modelo de datos de FHIR R4, ¿cuál es la relación entre los tipos de datos "CodeableConcept" y "Coding"?',
'{
  "code": {
    "coding": [
      { "system": "http://snomed.info/sct", "code": "386661006", "display": "Fever" },
      { "system": "http://hl7.org/fhir/sid/icd-10", "code": "R50.9", "display": "Fever, unspecified" }
    ],
    "text": "Fiebre persistente"
  }
}',
'Un CodeableConcept representa un concepto clínico que puede estar expresado en uno o más códigos (Coding) de diferentes sistemas terminológicos (ej. SNOMED CT e ICD-10) y opcionalmente un texto libre representativo (text).',
'https://hl7.org/fhir/R4/datatypes.html#CodeableConcept', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(4, 'A', 'Un Coding contiene múltiples CodeableConcept para permitir sinónimos locales.', 0, 'Es al revés: CodeableConcept contiene una lista de elementos Coding (0..*).'),
(4, 'B', 'Un CodeableConcept contiene 0..* elementos Coding y un texto libre opcional describiendo el concepto.', 1, 'Correcto: CodeableConcept agrupa codificaciones alternativas del mismo concepto y texto explicativo.'),
(4, 'C', 'CodeableConcept es exclusivo de FHIR R5 y reemplaza completamente a Coding de R4.', 0, 'Falso: CodeableConcept y Coding existen tanto en DSTU2, STU3, R4 como en R5.'),
(4, 'D', 'Coding permite texto libre del usuario pero CodeableConcept sólo permite códigos numéricos.', 0, 'Falso: el atributo "text" pertenece a CodeableConcept, mientras que Coding tiene "display".');

-- Pregunta 5: RESOURCES - Cardinalidades y Referencias en Encounter
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(5, 3, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'En el recurso Encounter de FHIR R4, ¿cuál de los siguientes elementos es de presencia obligatoria (cardinalidad 1..1)?',
NULL,
'En Encounter de FHIR R4, "status" tiene cardinalidad 1..1 (código de estado: planned, arrived, triaged, in-progress, onleave, finished, cancelled, etc.) y "class" tiene cardinalidad 1..1 (Coding: inpatient, outpatient, ambulatory, emergency, etc.). "subject" es 0..1.',
'https://hl7.org/fhir/R4/encounter.html', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(5, 'A', 'subject (referencia al paciente)', 0, 'En R4, subject en Encounter tiene cardinalidad 0..1 (p. ej. encuentros anónimos o no vinculados a paciente).'),
(5, 'B', 'status y class', 1, 'Correcto: ambos campos son obligatorios (1..1) en la definición canónica del recurso Encounter.'),
(5, 'C', 'participant (profesionales participantes)', 0, 'participant tiene cardinalidad 0..*.'),
(5, 'D', 'reasonCode o reasonReference', 0, 'El motivo del encuentro tiene cardinalidad 0..*.');

-- Pregunta 6: TERMINOLOGY - Binding Strengths
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(6, 4, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'En el modelado de conformidad y terminología en FHIR, ¿cuál es el significado del nivel de ligadura (binding strength) "required"?',
NULL,
'El binding "required" exige que las instancias válidas utilicen EXCLUSIVAMENTE uno de los códigos definidos en el ValueSet especificado. Si se utiliza un código ajeno al ValueSet, el recurso es inválido y no conforme.',
'https://hl7.org/fhir/R4/terminologies.html#strength', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(6, 'A', 'Los implementadores deben usar el código si lo conocen, pero son libres de sustituirlo por cualquier texto.', 0, 'Eso describe el binding "example".'),
(6, 'B', 'Se recomienda fuertemente el código, pero se admiten otros códigos si el ValueSet carece del término requerido.', 0, 'Eso describe el binding "extensible".'),
(6, 'C', 'Para ajustarse a la especificación, las instancias DEBEN usar uno de los códigos provistos en el ValueSet.', 1, 'Correcto: "required" es absoluto; no se admiten códigos fuera del ValueSet.'),
(6, 'D', 'El código es obligatorio sólo cuando el recurso se serializa en XML, no en JSON.', 0, 'La validación de conformidad y bindings es independiente del formato de serialización.');

-- Pregunta 7: SECURITY_IMPL - Tipos de Bundle en FHIR
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(7, 6, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'¿Cuál es la diferencia fundamental entre un Bundle de tipo "transaction" y uno de tipo "batch"?',
'{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [ ... ]
}',
'En un Bundle de tipo "transaction", todas las operaciones contenidas se ejecutan de manera atómica: o se procesan todas con éxito o, si una sola falla, toda la transacción se revierte (rollback). En un Bundle "batch", las operaciones son independientes y los fallos individuales no revierten las demás.',
'https://hl7.org/fhir/R4/bundle.html#bundle-types', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(7, 'A', 'Un Bundle "transaction" se procesa de forma atómica (todo o nada); un "batch" procesa cada entrada independientemente.', 1, 'Correcto: la atomicidad distingue a "transaction" de "batch".'),
(7, 'B', 'Un "batch" sólo admite peticiones GET y un "transaction" sólo admite POST y PUT.', 0, 'Falso: ambos admiten operaciones CRUD completas en sus entradas.'),
(7, 'C', '"transaction" sólo puede contener hasta 10 recursos, mientras que "batch" no tiene límite.', 0, 'Falso: la especificación no fija un límite arbitrario de 10 entradas para transacciones.'),
(7, 'D', 'No hay diferencia en el servidor; "transaction" es un término en desuso en FHIR R4.', 0, 'Falso: ambos tipos son fundamentales y están plenamente vigentes.');

-- Pregunta 8: REST_SEARCH - Modificador :exact y :contains
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(8, 2, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 2,
'Al realizar una búsqueda por nombre de paciente: GET [base]/Patient?name:exact=Garcia, ¿cuál de las siguientes afirmaciones describe el comportamiento del servidor?',
'GET [base]/Patient?name:exact=Garcia',
'El modificador :exact aplica coincidencia exacta sensible a mayúsculas/minúsculas y acentos sobre el valor completo del campo, sin comodines implícitos. Por tanto, coincide con "Garcia" pero NO con "garcia", "García" ni "Garcia Lopez".',
'https://hl7.org/fhir/R4/search.html#string', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(8, 'A', 'Retorna todos los pacientes cuyo nombre contenga la subcadena "garcia" en cualquier posición (insensible a mayúsculas).', 0, 'Ese es el comportamiento del modificador :contains.'),
(8, 'B', 'Coincide exactamente con la cadena "Garcia", respetando mayúsculas, minúsculas y acentuación en todo el valor.', 1, 'Correcto: :exact exige concordancia literal exacta sensible a caja y diacríticos.'),
(8, 'C', 'Coincide con "Garcia" y automáticamente con su forma fonética Soundex.', 0, 'Falso: el modificador para fonética es :phonetic.'),
(8, 'D', 'Genera un error 400 Bad Request porque los modificadores no están permitidos en parámetros de cadena.', 0, 'Falso: :exact es un modificador oficial plenamente soportado para parámetros de tipo string.');

-- Pregunta 9: PROFILING - StructureDefinition y Slicing
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(9, 5, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 3,
'En un perfil de StructureDefinition, ¿para qué se utiliza la técnica de "Slicing" (fraccionamiento) sobre un elemento de cardinalidad repetible (0..*)?',
'{
  "sliceName": "nationalId",
  "min": 1,
  "max": "1"
}',
'El Slicing permite dividir una lista de elementos repetibles (como identifier, telecom o extension) en diferentes subgrupos o "slices" definidos por reglas de discriminación (discriminator), aplicando restricciones de cardinalidad, tipos o bindings específicos a cada subgrupo.',
'https://hl7.org/fhir/R4/profiling.html#slicing', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(9, 'A', 'Para dividir un recurso en múltiples páginas cuando supera los 2 MB de tamaño.', 0, 'Falso: la paginación se realiza en Bundle mediante enlaces "next" y "previous".'),
(9, 'B', 'Para fragmentar elementos repetibles aplicando reglas diferenciadas y restricciones específicas a subconjuntos definidos.', 1, 'Correcto: slicing diferencia elementos repetibles por discriminadores.'),
(9, 'C', 'Para eliminar elementos obsoletos entre diferentes versiones de FHIR.', 0, 'Falso: los perfiles no eliminan campos canónicos del estándar base.'),
(9, 'D', 'Para comprimir arrays JSON en formatos binarios antes de persistirlos.', 0, 'Falso: el slicing es un concepto de modelado y validación de perfiles, no compresión.');

-- Pregunta 10: FOUNDATIONS - Niveles de Madurez de Recursos (FMM)
INSERT INTO questions (id, topic_id, parent_question_id, source, question_type, difficulty, stem, code_snippet, explanation, fhir_spec_url, is_verified) VALUES
(10, 1, NULL, 'MOCK_EXAM', 'SINGLE_CHOICE', 1,
'¿Qué significa que un recurso o especificación de FHIR tenga el estado "Normative" en lugar de un nivel FMM (FHIR Maturity Model 0 a 5)?',
NULL,
'El estado Normative indica que el recurso ha alcanzado la máxima estabilidad tras amplia prueba en producción y proceso formal de votación de estándares. Las futuras versiones de FHIR no introducirán cambios incompatibles con versiones anteriores (breaking changes) en contenidos normativos sin un proceso extraordinario.',
'https://hl7.org/fhir/R4/versions.html#maturity', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct, distractor_reason) VALUES
(10, 'A', 'Significa que el recurso es un borrador experimental que aún no debe usarse en entornos sanitarios.', 0, 'Ese estado es "Draft" o FMM 0.'),
(10, 'B', 'Indica que el recurso ha superado la fase de prueba y sus futuras revisiones garantizan retrocompatibilidad sin breaking changes.', 1, 'Correcto: "Normative" representa la máxima madurez y compromiso de estabilidad de HL7.'),
(10, 'C', 'Significa que el recurso ha sido deprecado y será eliminado en la siguiente versión.', 0, 'Ese estado es "Deprecated".'),
(10, 'D', 'Indica que el recurso requiere pago de licencia comercial a HL7 para su despliegue.', 0, 'Falso: FHIR es un estándar abierto con licencia Creative Commons / Open Source.');

-- Inicializar estado SRS para preguntas iniciales
INSERT INTO question_srs_state (question_id, consecutive_correct, easiness_factor, interval_days, next_review_date)
SELECT id, 0, 2.5, 1, date('now') FROM questions;
