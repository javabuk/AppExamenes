# 📘 Manual de Usuario Detallado — HL7 FHIR Certification Trainer & Simulator

Bienvenido al manual oficial de uso y administración de **HL7 FHIR Certification Trainer & Simulator**, la plataforma integral de estudio, práctica técnica y simulación de exámenes para la certificación oficial **HL7 FHIR R4**.

---

## Índice de Contenidos
1. [Puesta en Marcha y Acceso](#1-puesta-en-marcha-y-acceso)
2. [Recorrido por la Interfaz Principal](#2-recorrido-por-la-interfaz-principal)
3. [Cómo Incluir Nuevos Temas en el Syllabus](#3-cómo-incluir-nuevos-temas-en-el-syllabus)
4. [Cómo Incluir Nuevas Preguntas (3 Métodos)](#4-cómo-incluir-nuevas-preguntas-3-métodos)
5. [Guía de los 4 Modos de Simulación y Examen](#5-guía-de-los-4-modos-de-simulación-y-examen)
6. [Herramientas Prácticas FHIR R4 (REST Search & HAPI Inspector)](#6-herramientas-prácticas-fhir-r4-rest-search--hapi-inspector)
7. [Repetición Espaciada (Flashcards SM-2)](#7-repetición-espaciada-flashcards-sm-2)
8. [Ingesta de Materiales y PDFs del Curso (Apache PDFBox)](#8-ingesta-de-materiales-y-pdfs-del-curso-apache-pdfbox)
9. [Interpretación de Métricas y Semáforo de Preparación](#9-interpretación-de-métricas-y-semáforo-de-preparación)

---

## 1. Puesta en Marcha y Acceso

### Requisitos del Sistema
- **Java 21** instalado en el sistema.
- **Navegador Web moderno** (Chrome, Edge, Firefox).
- Base de datos autocontenida **SQLite** (se crea y migra automáticamente en `./data/fhir_exam.db`).

### Iniciar el Servidor
Desde una terminal PowerShell ubicada en la raíz del proyecto (`c:\Area\Formacion\FHIR\AppExamenes`):

```powershell
Remove-Item env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue
Remove-Item env:LOGGING_CONFIG -ErrorAction SilentlyContinue
java -jar target\fhir-exam-app-1.0.0.jar
```

Una vez iniciado el servidor, abre tu navegador en:
👉 **`http://localhost:8080/`**

---

## 2. Recorrido por la Interfaz Principal

El menú lateral izquierdo organiza el estudio en **3 áreas**:

1. **Panel de Control y Exámenes:**
   - **📊 Dashboard & Analítica:** Visión global de tu preparación (*Readiness Score*), mapa de calor por áreas del examen oficial e historial de intentos.
   - **🎯 Modos de Examen:** Acceso rápido a los 4 motores de evaluación o configuración de sesiones personalizadas.
2. **Práctica Técnica FHIR:**
   - **🔍 Constructor REST Search:** Entrenador interactivo para escribir y validar consultas `GET [base]/Resource?...` según el estándar FHIR R4.
   - **🧬 Inspector HAPI FHIR:** Validador sintáctico y estructural de recursos JSON (`Patient`, `Observation`, `Bundle`, etc.) usando el motor oficial HAPI FHIR R4.
   - **🗂️ Flashcards SM-2:** Tarjetas de memoria con algoritmo SuperMemo-2 para repasar conceptos programados para el día actual.
3. **Gestión de Contenidos:**
   - **📚 Banco de Preguntas:** Explorador completo del repositorio de preguntas, creación de temas, alta manual de preguntas, importación masiva JSON y generador de preguntas espejo (*Clone & Twist*).
   - **📑 Materiales & PDFBox:** Subida e indexación automática de apuntes y documentos PDF.

---

## 3. Cómo Incluir Nuevos Temas en el Syllabus

Por defecto, la aplicación incluye los **6 bloques oficiales de HL7 FHIR** (`FOUNDATIONS`, `REST_SEARCH`, `RESOURCES`, `TERMINOLOGY`, `PROFILING`, `SECURITY_IMPL`). Puedes añadir nuevos temas personalizados mediante **3 vías**:

### Opción A: Desde la Interfaz Web (Recomendado)
1. En el menú izquierdo, haz clic en **📚 Banco de Preguntas**.
2. En la esquina superior derecha, pulsa el botón morado **`+ Nuevo Tema`**.
3. Completa los campos del formulario:
   - **Código Único:** Identificador corto en mayúsculas sin espacios (ej. `FHIRPATH`, `SMART_AUTH`, `CDS_HOOKS`).
   - **Nombre del Tema:** Descripción legible (ej. *Expresiones FHIRPath y Operaciones Extendidas*).
   - **Peso en el Examen (%):** Ponderación relativa que tendrá este tema en los simulacros oficiales (ej. `10` o `15`).
4. Pulsa **Guardar Tema**. El tema aparecerá inmediatamente en el **Mapa de Calor del Dashboard** y en todos los desplegables de la aplicación.

### Opción B: Mediante la API REST (`POST /api/topics`)
Puedes registrar temas desde Postman, `curl` o scripts externos:

```bash
curl -X POST http://localhost:8080/api/topics \
  -H "Content-Type: application/json" \
  -d '{
    "code": "FHIRPATH",
    "name": "Expresiones FHIRPath y Operaciones Extendidas",
    "examWeightPercent": 10.0,
    "studyStatus": "PENDING"
  }'
```

Para actualizar el nombre, peso o estado de un tema existente (`PUT /api/topics/{id}`):
```bash
curl -X PUT http://localhost:8080/api/topics/7 \
  -H "Content-Type: application/json" \
  -d '{
    "examWeightPercent": 12.5,
    "studyStatus": "IN_PROGRESS"
  }'
```

### Opción C: Mediante Migración SQL (Flyway) o SQLite
Si deseas que el tema forme parte permanente de la semilla inicial de base de datos, puedes añadir un `INSERT` en `src/main/resources/db/migration/V2__seed_fhir_data.sql` (o crear `V3__custom_topics.sql`):

```sql
INSERT INTO syllabus_topics (code, name, exam_weight_percent, study_status)
VALUES ('FHIRPATH', 'Expresiones FHIRPath y Operaciones Extendidas', 10.0, 'PENDING');
```

---

## 4. Cómo Incluir Nuevas Preguntas (4 Métodos)

Cada pregunta creada se vincula automáticamente a un tema del Syllabus y se inicializa en el motor de repetición espaciada (**SM-2**) para su seguimiento.

### Método 1: Formulario Gráfico Individual (`+ Nueva Pregunta`)
1. Ve a la pestaña **📚 Banco de Preguntas**.
2. Haz clic en el botón azul **`+ Nueva Pregunta`**.
3. Rellena los datos:
   - **Tema:** Selecciona el tema al que pertenece (se muestra el `[ID]` y nombre de cada tema).
   - **Dificultad:** `1 - Básico`, `2 - Nivel Examen Oficial` o `3 - Avanzado / Arquitecto`.
   - **Enunciado de la Pregunta (Stem):** Caso clínico o pregunta técnica.
   - **Fragmento de Código / URL FHIR (Opcional):** Fragmento JSON o petición HTTP que aparecerá formateado en consola oscura.
   - **Opciones A, B, C, D:**
     - Marca con el selector circular (**Radio Button**) cuál de las 4 letras es la **respuesta correcta**.
     - Escribe el texto de cada opción.
     - *(Opcional pero recomendado)* En el campo derecho de cada opción, indica la **razón de acierto o distractor** (explica al alumno por qué esa opción es falsa o verdadera cuando revise el examen).
   - **Explicación Detallada:** Justificación normativa completa.
   - **URL Especificación HL7 FHIR:** Enlace directo a la documentación oficial (ej. `https://hl7.org/fhir/R4/search.html`).
4. Pulsa **Guardar Pregunta**.

---

### Método 2: Importación Masiva en Formato JSON (`📥 Importar JSON`)
Ideal para cargar decenas de preguntas de una sola vez (por ejemplo, generadas a partir de tus apuntes).

1. Ve a **📚 Banco de Preguntas** y pulsa **`📥 Importar JSON`**.
2. Puedes pulsar en **"Cargar Plantilla de Ejemplo"** para ver la estructura exacta requerida.
3. Pega un **array JSON** con tus preguntas siguiendo este esquema:

```json
[
  {
    "topicId": 2,
    "stem": "¿Qué parámetro de resultado en una búsqueda FHIR R4 permite solicitar al servidor únicamente el número total de coincidencias sin devolver los recursos?",
    "codeSnippet": "GET [base]/Observation?_summary=count",
    "explanation": "El parámetro _summary=count indica al servidor que devuelva un Bundle con el atributo total poblado pero sin entradas entry.",
    "fhirSpecUrl": "https://hl7.org/fhir/R4/search.html#summary",
    "difficulty": 2,
    "questionType": "SINGLE_CHOICE",
    "source": "MANUAL",
    "options": [
      {
        "optionLabel": "A",
        "optionText": "_count=0",
        "correct": false,
        "distractorReason": "_count=0 limita las entradas por página, pero el parámetro estándar de resumen de conteo es _summary=count."
      },
      {
        "optionLabel": "B",
        "optionText": "_summary=count",
        "correct": true,
        "distractorReason": "Correcto: _summary=count devuelve únicamente el total de recursos coincidentes."
      },
      {
        "optionLabel": "C",
        "optionText": "_total=only",
        "correct": false,
        "distractorReason": "_total controla la precisión del conteo (none, estimate, accurate), no existe el valor 'only'."
      },
      {
        "optionLabel": "D",
        "optionText": "_elements=total",
        "correct": false,
        "distractorReason": "_elements filtra elementos de los recursos devueltos, no el metadato del Bundle."
      }
    ]
  }
]
```

> **Referencia de `topicId` por defecto:**
> - `1`: Fundamentos, Arquitectura y Tipos de Datos FHIR (`FOUNDATIONS`)
> - `2`: Intercambio RESTful, Verbos HTTP y Búsqueda Avanzada (`REST_SEARCH`)
> - `3`: Recursos Clínicos, Administrativos y Referencias (`RESOURCES`)
> - `4`: Terminologías, CodeSystem, ValueSet y ConceptMap (`TERMINOLOGY`)
> - `5`: Conformidad, StructureDefinition, Extensiones e IGs (`PROFILING`)
> - `6`: Seguridad, SMART on FHIR, Bundles y Transacciones (`SECURITY_IMPL`)
> *(Si creaste nuevos temas, verás su `[ID X]` en el selector de temas del formulario).*

4. Haz clic en **Importar Ahora**.

---

### Método 3: Generación Automática "Clone & Twist" (Preguntas Espejo)
Cuando fallas una pregunta en un examen o quieres reforzar un concepto del Banco de Preguntas:
1. En la pantalla de **Resultados del Examen** o en el **📚 Banco de Preguntas**, localiza la pregunta.
2. Haz clic en el botón **`✨ Clonar y Generar Variante`** (o **`✨ Generar Variante Espejo (Clone & Twist)`**).
3. El sistema creará automáticamente una nueva pregunta vinculada a la original (`source: AI_GENERATED`), invirtiendo el orden de las alternativas, rotando las etiquetas (`A`, `B`, `C`, `D`) y planteando un giro sobre el escenario original para evitar que memorices visualmente la posición de la letra correcta.

---

### Método 4: Vía API REST (`POST /api/questions` o `POST /api/questions/import`)
- **Alta individual:** `POST http://localhost:8080/api/questions` enviando un objeto JSON con la estructura de una pregunta.
- **Alta masiva:** `POST http://localhost:8080/api/questions/import` enviando una lista JSON `[ ... ]`.
- **Eliminar una pregunta:** `DELETE http://localhost:8080/api/questions/{id}`.

---

## 5. Guía de los 4 Modos de Simulación y Examen

Puedes iniciar un examen desde las tarjetas rápidas de **🎯 Modos de Examen** o pulsando **`+ Nuevo Examen`** en la barra superior:

| Modo | Nombre | Funcionamiento |
| :--- | :--- | :--- |
| **1. `SIMULATION`** | **Simulacro Oficial** | Selecciona preguntas respetando estrictamente el peso porcentual de cada tema del Syllabus oficial. Incluye **cronómetro regresivo** (90 segundos por pregunta) y oculta las soluciones hasta que entregas el examen completo. |
| **2. `ADAPTIVE`** | **Cazador de Puntos Débiles** | Algoritmo adaptativo que compone el examen con: **60%** preguntas de tus temas en rojo (<65% de acierto), **25%** preguntas que has fallado anteriormente y **15%** preguntas nuevas o menos vistas. |
| **3. `TUTOR`** | **Modo Tutor (Sin Tiempo)** | Ideal para estudio activo. Al seleccionar una opción, aparece el botón **"💡 Comprobar Respuesta (Modo Tutor)"**, mostrando al instante si acertaste, la explicación normativa y el análisis de cada distractor antes de pasar a la siguiente pregunta. |
| **4. `ERRORS`** | **Maratón de Fallos** | Selecciona exclusivamente las preguntas registradas como falladas en tu historial para limpiar tu "bandeja de errores pendientes". |

### Controles durante el Examen
- **Navegador de Preguntas (Panel Derecho):** Muestra en verde las preguntas respondidas y con un punto ámbar las marcadas con duda.
- **🚩 Marcar con duda:** Permite señalar preguntas para revisarlas antes de entregar.
- **Tiempo invertido:** El sistema mide silenciosamente cuántos segundos dedicas a cada pregunta para detectar fatiga o temas que te hacen dudar.

---

## 6. Herramientas Prácticas FHIR R4 (REST Search & HAPI Inspector)

### 🔍 Constructor y Validador REST Search
Permite practicar uno de los bloques con mayor peso en el examen oficial (25%):
1. Selecciona el **Recurso Esperado** (ej. `Observation`, `Patient`, `Condition`) o carga uno de los **Retos Rápidos** (`Reto 1` a `Reto 4`).
2. Escribe tu consulta en la barra (ej. `GET [base]/Observation?code=http://loinc.org|8867-4&status=final&_include=Observation:subject`).
3. Pulsa **Validar Sintaxis**.
4. El motor analizará:
   - Si el tipo de recurso coincide con el esperado.
   - Si los modificadores (`:exact`, `:contains`, `:missing`, `:above`, `:below`, `:text`) están bien aplicados.
   - Si los prefijos de fecha/cantidad (`ge`, `le`, `gt`, `lt`, `eq`, `ne`) y tokens `system|code` son válidos.
   - Si la sintaxis de `_include` / `_revinclude` cumple el formato `Origen:parametro[:destino]`.

### 🧬 Inspector y Validador de Recursos (HAPI FHIR R4)
1. Pega cualquier JSON de un recurso FHIR R4 o pulsa los botones de prueba (**Cargar Patient Válido**, **Cargar Observation con Error**, **Cargar Bundle Transaction**).
2. Pulsa **Ejecutar Validación HAPI FHIR**.
3. El parser oficial de **HAPI FHIR R4** verificará la sintaxis JSON, tipos de datos primitivos/complejos y reglas obligatorias de cardinalidad (por ejemplo, detectará si a un `Observation` le falta `status` o `code`, o si a un `Bundle` de tipo `transaction` le falta el bloque `request` en sus entradas).

---

## 7. Repetición Espaciada (Flashcards SM-2)

En la pestaña **🗂️ Flashcards SM-2** (con contador de tarjetas pendientes en el menú lateral):
1. Lee el enunciado y el fragmento de código en el anverso de la tarjeta.
2. Piensa la respuesta y pulsa **"Mostrar Solución y Explicación"**.
3. Evalúa honestamente tu recuerdo en la escala **SuperMemo-2 (0 a 5)**:
   - **`0` (Olvido) / `1` (Error) / `2` (Difícil):** Reinicia la racha de la pregunta y programa su repaso para **mañana (1 día)**.
   - **`3` (Bueno) / `4` (Fácil) / `5` (Perfecto):** Incrementa la racha consecutiva, ajusta el *Easiness Factor (EF)* y multiplica el intervalo de repaso (1 día → 6 días → $6 \times EF$ días...).
   - Cuando una pregunta alcanza **2 o más aciertos consecutivos**, pasa a contabilizarse como **Pregunta Dominada** en el Dashboard.

---

## 8. Ingesta de Materiales, Descarga por URL y PDFs (Apache PDFBox & Web Crawler)

En la pestaña **📑 Materiales & PDFBox** dispones de dos modalidades de ingesta:

### Modalidad A: Descargar e Indexar desde URL Web (ej: HL7 FHIR Spec)
1. Activa la pestaña **`🌐 Importar desde URL`**.
2. Introduce la URL web de la documentación (por ejemplo: `https://www.hl7.org/fhir/references.html` o un enlace a un `.pdf` remoto). Puedes pulsar **"Cargar Ejemplo"** para probar la URL de referencias oficial de HL7 FHIR.
3. Asigna el **Tema del Syllabus** correspondiente y opcionalmente un título descriptivo.
4. Pulsa **`📥 Descargar, Extraer e Indexar Contenido`**.
5. El motor conectará a la web, descargará el recurso, limpiará cabeceras, scripts y elementos de navegación, extrayendo el contenido técnico puro e indexándolo en la base de datos con su enlace de referencia canónico.

### Modalidad B: Carga de Archivos PDF Locales (Apache PDFBox)
1. Activa la pestaña **`📁 Cargar PDF Local`**.
2. Selecciona el **Tema Asociado** y escribe un **Título / Capítulo**.
3. Selecciona cualquier archivo `.pdf` de tus apuntes o cursos.
4. Pulsa **"Procesar e Indexar con PDFBox"**.
5. El motor **Apache PDFBox 3.0** extraerá el texto íntegro preservando el flujo de párrafos y registrando el conteo de palabras.

### Cómo Trabajar con los Materiales Indexados
- **Buscador en tiempo real:** Escribe cualquier palabra clave en la barra superior para filtrar al instante qué documentos contienen ese concepto.
- **Visor interactivo completo:** Pulsa **`📖 Leer Documento Completo`** para abrir el modal con el texto íntegro, conteo de palabras, enlace a la URL web original y botón **`📋 Copiar Texto`**.
- **Práctica integrada:** Pulsa **`🎯 Practicar Preguntas del Tema`** para lanzar una sesión de examen focalizada en las preguntas vinculadas a ese material.
- **Borrado:** Pulsa **`🗑️ Eliminar`** para suprimir materiales que ya no necesites.

---

## 9. Interpretación de Métricas y Semáforo de Preparación

En **📊 Dashboard & Analítica** encontrarás los indicadores clave para saber cuándo estás listo para presentarte al examen oficial:

- **Índice de Preparación (*Readiness Score*):**
  $$\text{Readiness} = 50\% \text{ Precisión Ponderada por Tema} + 30\% \text{ Media en Simulacros} + 20\% \text{ Dominio SRS}$$
  - **🟢 ≥ 80% (`Preparado para Aprobar`):** Margen de seguridad óptimo para superar el corte oficial de HL7 (70%).
  - **🟡 65% – 79% (`En Buen Camino`):** Conocimiento sólido pero con áreas específicas que requieren repaso.
  - **🔴 < 65% (`Necesita Refuerzo`):** Se recomienda usar el modo **Cazador de Puntos Débiles** y **Modo Tutor**.
- **Mapa de Calor del Syllabus:** Muestra cada tema con su porcentaje de acierto, su peso oficial en el examen, el tiempo medio de respuesta en segundos y un botón directo **`Practicar Tema →`** para lanzar un entrenamiento focalizado en ese bloque.
