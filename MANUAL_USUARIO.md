# 📘 Manual de Usuario Detallado — HL7 FHIR Certification Trainer & Simulator (Plataforma Multi-Certificación y Multiusuario)

Bienvenido al manual oficial de uso y administración de **HL7 FHIR Certification Trainer & Simulator (FHIR Cert Master)**, la plataforma integral multiusuario y multi-certificación de estudio, práctica técnica, simulación de exámenes y analítica de progreso para certificaciones oficiales **HL7 FHIR R4, HL7 FHIR R5** y estándares de interoperabilidad adicionales.

---

## Índice de Contenidos
1. [Puesta en Marcha, Autenticación y Roles](#1-puesta-en-marcha-autenticación-y-roles)
2. [Arquitectura Multi-Certificación y Selector de Estándar](#2-arquitectura-multi-certificación-y-selector-de-estándar)
3. [Recorrido por la Interfaz Principal](#3-recorrido-por-la-interfaz-principal)
4. [Modelo de Propiedad y Aislamiento de Datos por Usuario y Certificación](#4-modelo-de-propiedad-y-aislamiento-de-datos-por-usuario-y-certificación)
5. [Gestión y Configuración del Syllabus Oficial](#5-gestión-y-configuración-del-syllabus-oficial)
6. [Cómo Incluir Nuevas Preguntas (4 Métodos)](#6-cómo-incluir-nuevas-preguntas-4-métodos)
7. [Guía de los 4 Modos de Simulación y Examen](#7-guía-de-los-4-modos-de-simulación-y-examen)
8. [Herramientas Prácticas Versionadas (REST Search & Validador HAPI R4/R5)](#8-herramientas-prácticas-versionadas-rest-search--validador-hapi-r4r5)
9. [Repetición Espaciada (Flashcards SM-2)](#9-repetición-espaciada-flashcards-sm-2)
10. [Ingesta de Materiales, Fichas de Estudio y Notas Personales](#10-ingesta-de-materiales-fichas-de-estudio-y-notas-personales)
11. [Interpretación de Métricas, Ponderación y Semáforo de Preparación](#11-interpretación-de-métricas-ponderación-y-semáforo-de-preparación)
12. [Panel de Administración (Solo Rol ADMIN)](#12-panel-de-administración-solo-rol-admin)

---

## 1. Puesta en Marcha, Autenticación y Roles

### Requisitos del Sistema
- **Java 21** instalado en el sistema.
- **Navegador Web moderno** (Chrome, Edge, Firefox).
- Base de datos autocontenida **SQLite** (se crea y migra automáticamente con Flyway V1–V7 en `./data/fhir_exam.db`).

### Iniciar el Servidor
Puedes arrancar la plataforma mediante los scripts incluidos para Windows 11:
- **Doble clic en `start.bat`** o desde PowerShell:
  ```powershell
  .\start.ps1
  ```
- O manualmente:
  ```powershell
  Remove-Item env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue
  Remove-Item env:LOGGING_CONFIG -ErrorAction SilentlyContinue
  java -jar target\fhir-exam-app-1.0.0.jar
  ```

Una vez iniciado el servidor, abre tu navegador en el puerto configurado en `app.conf` (por defecto **`http://localhost:8787/`** o `http://localhost:8080/`).

### Pantalla de Inicio de Sesión y Seguridad
- **Acceso protegido:** Sin sesión activa, la aplicación muestra únicamente la pantalla de inicio de sesión (*Login*).
- **Credenciales:** Puedes iniciar sesión indicando tu **nombre de usuario** o tu **correo electrónico** junto con tu contraseña.
- **Usuario Administrador Inicial (Bootstrap):**
  - En el primer arranque, si no existe ningún administrador en la base de datos, el sistema crea automáticamente el usuario definido en `APP_ADMIN_USER` (por defecto `admin`).
  - Si no se definió `APP_ADMIN_PASSWORD` en `app.conf` o variables de entorno, el servidor genera una **contraseña aleatoria segura**, la imprime **una única vez** en el log de arranque (`logs/app.log`) y activa el cambio obligatorio de contraseña en el primer acceso.
- **Política de contraseñas:** Todas las contraseñas deben tener **al menos 10 caracteres** y se almacenan cifradas con **BCrypt**.
- **Cambio obligatorio (`mustChangePassword`):** Cuando un administrador crea tu cuenta o resetea tu contraseña, el sistema te mostrará un diálogo obligatorio tras iniciar sesión para que definas tu propia contraseña personal antes de usar la plataforma.
- **Protección contra fuerza bruta:** Tras **5 intentos fallidos consecutivos** (configurable en `app.conf`), la cuenta se bloquea temporalmente durante **15 minutos** y el evento queda registrado en auditoría.

### Roles del Sistema
| Rol | Permisos y Alcance |
| :--- | :--- |
| **`USER`** *(Alumno / Usuario estándar)* | Realizar exámenes en los 4 modos, usar constructores prácticos (REST Search y validador HAPI FHIR), repasar su cola personal de Flashcards SM-2, crear notas privadas en las fichas de estudio, aportar preguntas y materiales al banco compartido (pudiendo editar/eliminar únicamente los que él mismo haya creado) y consultar **su propio** Dashboard, mapa de calor, *Readiness Score* e historial de intentos. |
| **`ADMIN`** *(Administrador / Formador)* | Todo lo disponible para `USER` más acceso al bloque **Administración**: gestión completa de usuarios (alta, edición, activación/desactivación lógica y reseteo de contraseña), edición/borrado de cualquier pregunta o material del banco, consulta de resultados y trazabilidad detallada de **cualquier usuario**, analítica global agregada, exportación CSV y consulta del registro de auditoría. |

---

## 2. Arquitectura Multi-Certificación y Selector de Estándar

La plataforma soporta la preparación simultánea de múltiples programas y certificaciones de interoperabilidad sanitaria (por ejemplo, **HL7 FHIR R4**, **HL7 FHIR R5**, **HL7 v2.x**, **CDA**, etc.).

### 2.1. Selector de Certificación en Cabecera
En la barra superior, junto a la información de tu perfil, se ubica el **Selector de Certificación Activa**:
- **Múltiples certificaciones asignadas:** Se presenta un desplegable con el icono, código y versión de cada una de las certificaciones que tienes asignadas. Al seleccionar una certificación, la plataforma conmuta inmediatamente todo tu entorno de trabajo (Dashboard, Banco de preguntas, Exámenes, Flashcards y Materiales).
- **Una única certificación:** Se muestra como una insignia identificativa fija (`🧬 FHIR_R4`).
- **Persistencia automática:** Tu elección se almacena en el navegador (`localStorage`) y se inyecta de forma transparente en cada llamada a la API a través de la cabecera HTTP `X-Certification`.
- **Aislamiento de seguridad RBAC:** Un usuario alumno (`USER`) solo puede operar sobre aquellas certificaciones que le hayan sido asignadas activamente por un administrador; si intenta forzar el acceso a otra certificación, el servidor deniega la petición con error `403 Forbidden`. Los administradores (`ADMIN`) pueden conmutar libremente entre cualquier certificación del catálogo.

### 2.2. Parámetros Propios por Certificación
Cada certificación dispone de sus propios parámetros independientes:
- **Examen Oficial:** Nº de preguntas (ej. 50 en R4), duración cronometrada en minutos (ej. 90 min) y nota de corte (ej. 70.0%).
- **Syllabus Ponderado:** Áreas temáticas configurables con pesos porcentuales oficiales (%) que alimentan el cálculo del *Readiness Score* y el mapa de calor.
- **Documentación Oficial:** URL base de la especificación (ej. `https://hl7.org/fhir/R4/` vs `https://hl7.org/fhir/R5/`) empleada para generar enlaces en las justificaciones de respuesta.
- **Habilitación de Módulos (Feature Flags):** Cada certificación activa únicamente las herramientas aplicables (`EXAM`, `FLASHCARDS`, `MATERIALS`, `REST_SEARCH_BUILDER`, `RESOURCE_VALIDATOR`). Las herramientas no soportadas por la certificación se ocultan automáticamente en el menú lateral y quedan protegidas ante accesos no autorizados.

---

## 3. Recorrido por la Interfaz Principal

En la **barra superior derecha** encontrarás el **menú de perfil de usuario**, donde se muestra tu nombre, `@username`, insignia de rol (`USER` o `ADMIN`), el botón **`🔑 Cambiar Contraseña`** y el botón **`🚪 Salir`**.

El menú lateral izquierdo organiza el trabajo en **3 áreas para todos los usuarios** (más una **4ª área exclusiva para `ADMIN`**):

1. **Panel de Control y Exámenes:**
   - **📊 Dashboard & Analítica:** Visión global de tu preparación personal (*Readiness Score*), mapa de calor por áreas del examen oficial e historial de tus intentos con revisión detallada pregunta a pregunta.
   - **🎯 Modos de Examen:** Acceso rápido a los 4 motores de evaluación o configuración de sesiones personalizadas.
2. **Práctica Técnica FHIR:**
   - **🔍 Constructor REST Search:** Entrenador interactivo para escribir y validar consultas `GET [base]/Resource?...` según el estándar FHIR R4.
   - **🧬 Inspector HAPI FHIR:** Validador sintáctico y estructural de recursos JSON (`Patient`, `Observation`, `Bundle`, etc.) usando el motor oficial HAPI FHIR R4.
   - **🗂️ Flashcards SM-2:** Tarjetas de memoria con algoritmo SuperMemo-2 calculadas exclusivamente sobre tu progreso individual.
3. **Gestión de Contenidos:**
   - **📚 Banco de Preguntas:** Explorador del repositorio compartido de preguntas, alta manual, importación masiva JSON y generador de preguntas espejo (*Clone & Twist*).
   - **📑 Materiales & Fichas:** Subida e indexación de apuntes, documentos PDF, páginas web y presentación interactiva de fichas con notas Markdown privadas por usuario.
4. **Administración *(Solo visible y accesible para `ADMIN`)*:**
   - **👥 Usuarios:** Alta, edición, activación/baja lógica y reseteo de contraseñas.
   - **📈 Resultados por Usuario:** KPIs por alumno e inspección completa de los intentos, respuestas, tiempos, mapa de calor y preguntas más falladas de cualquier usuario.
   - **🌐 Vista Agregada:** Analítica global del grupo, precisión media por bloque del syllabus, ranking comparativo y top de preguntas con mayor tasa de fallo.
   - **📋 Auditoría:** Registro cronológico de eventos de seguridad y acciones administrativas.

---

## 3. Modelo de Propiedad y Aislamiento de Datos por Usuario

- **Datos estrictamente privados por usuario:**
  - Intentos de examen (`exam_attempts`), respuestas elegidas, tiempos por pregunta y marcas de duda (`exam_attempt_answers`).
  - Historial de fallos para el modo *Maratón de Fallos* y cálculo del modo *Adaptativo*.
  - Estado de repetición espaciada SuperMemo-2 (`question_srs_state`: factor de facilidad, intervalo, racha y fecha de próximo repaso).
  - Comentarios y notas Markdown añadidas en las fichas de estudio (`study_card_comments`).
  - Indicadores del Dashboard (*Readiness Score*, mapa de calor, estadísticas por material).
- **Contenido compartido con autoría (`owner_id`):**
  - El **Banco de Preguntas** y los **Materiales de Estudio** son **compartidos** para que todos los usuarios se beneficien del repositorio común.
  - Cada pregunta y material muestra la etiqueta de su autor (`👤 Autor`).
  - Un usuario con rol `USER` solo ve los botones de **Editar / Eliminar** en las preguntas y materiales creados por él mismo. Un `ADMIN` puede gestionar todo el catálogo.
  - **Borrado lógico (*Soft Delete*):** Si se elimina una pregunta o material que ya fue utilizado en exámenes históricos de cualquier usuario, el sistema realiza una baja lógica (`deleted = true`) para que deje de aparecer en nuevos exámenes sin romper jamás el historial ni las estadísticas de los intentos pasados.

---

## 4. Cómo Incluir Nuevos Temas en el Syllabus

Por defecto, la aplicación incluye los **6 bloques oficiales de HL7 FHIR** (`FOUNDATIONS`, `REST_SEARCH`, `RESOURCES`, `TERMINOLOGY`, `PROFILING`, `SECURITY_IMPL`). Puedes añadir nuevos temas personalizados desde **📚 Banco de Preguntas** pulsando **`+ Nuevo Tema`** y completando:
- **Código Único:** Identificador corto en mayúsculas sin espacios (ej. `FHIRPATH`, `SMART_AUTH`, `CDS_HOOKS`).
- **Nombre del Tema:** Descripción legible (ej. *Expresiones FHIRPath y Operaciones Extendidas*).
- **Peso en el Examen (%):** Ponderación relativa que tendrá este tema en los simulacros oficiales.

---

## 5. Cómo Incluir Nuevas Preguntas (4 Métodos)

Cada pregunta creada registra automáticamente a tu usuario como propietario (`owner`) y queda disponible en el banco compartido.

### Método 1: Formulario Gráfico Individual (`+ Nueva Pregunta`)
1. Ve a **📚 Banco de Preguntas** y haz clic en **`+ Nueva Pregunta`**.
2. Selecciona el **Tema**, **Material asociado (opcional)** y **Dificultad** (`1 - Básico`, `2 - Nivel Examen Oficial`, `3 - Avanzado`).
3. Escribe el enunciado, fragmento de código opcional, las 4 opciones (`A`, `B`, `C`, `D`) marcando la correcta y la explicación normativa.

### Método 2: Importación Masiva en Formato JSON (`📥 Importar JSON`)
1. En **📚 Banco de Preguntas**, pulsa **`📥 Importar JSON`**.
2. Pulsa **"Cargar Plantilla de Ejemplo"** o pega tu array JSON de preguntas y haz clic en **Importar Ahora**. Todas las preguntas importadas quedarán asociadas a tu usuario como autor.

### Método 3: Generación Automática "Clone & Twist" (Preguntas Espejo)
En la revisión de resultados o en el **📚 Banco de Preguntas**, pulsa **`✨ Clonar y Generar Variante`** para crear una variante vinculada con las opciones rotadas.

### Método 4: Edición y Borrado según Propiedad
- Si eres el autor de la pregunta o tienes rol `ADMIN`, verás los botones **`✏️ Editar`** y **`🗑️ Borrar`** en la tarjeta de la pregunta.

---

## 6. Guía de los 4 Modos de Simulación y Examen

Puedes iniciar un examen desde **🎯 Modos de Examen** o pulsando **`+ Nuevo Examen`**:

| Modo | Nombre | Funcionamiento (calculado sobre los datos del usuario autenticado) |
| :--- | :--- | :--- |
| **1. `SIMULATION`** | **Simulacro Oficial** | Selecciona preguntas respetando el peso porcentual de cada tema del Syllabus oficial. Incluye **cronómetro regresivo** (90 segundos por pregunta) y oculta las soluciones hasta entregar. |
| **2. `ADAPTIVE`** | **Cazador de Puntos Débiles** | Compone el examen según **tu** historial personal: **60%** preguntas de tus temas en rojo (<65% de acierto), **25%** preguntas que tú has fallado anteriormente y **15%** preguntas nuevas o menos vistas por ti. |
| **3. `TUTOR`** | **Modo Tutor (Sin Tiempo)** | Permite comprobar al instante cada respuesta con su explicación normativa y análisis de distractores antes de pasar a la siguiente pregunta. |
| **4. `ERRORS`** | **Maratón de Fallos** | Selecciona exclusivamente las preguntas registradas como falladas en **tu** historial personal para limpiar tu bandeja de errores pendientes. |

En la tabla de **Historial Reciente de Intentos** de tu Dashboard puedes pulsar **`🔍 Ver Corrección`** en cualquier examen anterior para revisar todas tus respuestas, tiempos y explicaciones.

---

## 7. Herramientas Prácticas FHIR R4 (REST Search & HAPI Inspector)

- **🔍 Constructor y Validador REST Search:** Practica consultas `GET [base]/Resource?...` validando modificadores (`:exact`, `:contains`, `:missing`), prefijos de fecha/cantidad (`ge`, `le`, `gt`, `lt`) e inclusiones (`_include`, `_revinclude`).
- **🧬 Inspector y Validador de Recursos (HAPI FHIR R4):** Valida cualquier recurso JSON FHIR R4 con el parser oficial HAPI FHIR R4.

---

## 8. Repetición Espaciada (Flashcards SM-2)

En **🗂️ Flashcards SM-2**, la cola diaria de tarjetas pendientes se calcula de forma **individual para cada usuario**:
- **`0` a `2` (Olvido / Error / Difícil):** Reinicia tu racha en esa pregunta y programa su repaso para mañana (1 día).
- **`3` a `5` (Bueno / Fácil / Perfecto):** Incrementa tu racha consecutiva, ajusta tu *Easiness Factor (EF)* y multiplica el intervalo de repaso.
- Al alcanzar **2 o más aciertos consecutivos**, la pregunta se contabiliza como **Pregunta Dominada** en tu Dashboard personal.

---

## 9. Ingesta de Materiales, Fichas de Estudio y Notas Personales

En **📑 Materiales & Fichas** puedes:
- **Subir documentos PDF locales (Apache PDFBox)**, **importar documentación desde URLs web** o crear **colecciones de Fichas de Estudio en Markdown** estructuradas por capítulos (`##`) y fichas (`###`).
- **Notas y Comentarios Personales en Fichas:** Dentro del visor interactivo de fichas, cada usuario puede añadir sus propias **notas de ampliación en Markdown**. Estas notas son **privadas de cada usuario** (cada alumno solo ve, edita y elimina sus propios apuntes sobre cada ficha).
- **Edición y Borrado de Materiales:** El autor de un material (o un `ADMIN`) puede editar su título/contenido mediante el botón **`✏️ Editar`** o darle de baja con **`🗑️ Eliminar`**.

---

## 10. Interpretación de Métricas y Semáforo de Preparación

En **📊 Dashboard & Analítica** se calcula tu índice personal de preparación:
$$\text{Readiness} = 50\% \text{ Precisión Ponderada por Tema} + 30\% \text{ Media en Simulacros} + 20\% \text{ Dominio SRS}$$
- **🟢 ≥ 80% (`Preparado para Aprobar`):** Margen óptimo para superar el corte oficial de HL7 (70%).
- **🟡 65% – 79% (`En Buen Camino`):** Conocimiento sólido con áreas puntuales por reforzar.
- **🔴 < 65% (`Necesita Refuerzo`):** Se recomienda usar *Cazador de Puntos Débiles* y *Modo Tutor*.

---

## 12. Panel de Administración (Solo Rol ADMIN)

Los usuarios con rol **`ADMIN`** disponen de una sección adicional **Administración** en la barra lateral izquierda con cinco módulos:

### 12.1. 👥 Gestión de Usuarios y Asignación de Certificaciones (`Administración → Usuarios`)
- **Listado paginado y filtros:** Busca usuarios por nombre, `@username` o email, y filtra por rol (`ADMIN` / `USER`) o estado (`Activo` / `Desactivado`).
- **Crear Nuevo Usuario:** Define `username`, `email`, nombre visible, rol y **certificaciones asignadas** (selección múltiple con casillas de verificación). Si dejas en blanco la contraseña inicial, el sistema genera una **contraseña temporal aleatoria**, te la muestra en pantalla para que la entregues al usuario y marca automáticamente `mustChangePassword = true`.
- **Editar Usuario y Modificar Certificaciones:** Modifica nombre visible, email, rol y actualiza en tiempo real las certificaciones a las que tiene acceso el alumno.
- **Activar / Desactivar (Baja Lógica):** Nunca se borran físicamente los usuarios para conservar toda la trazabilidad histórica. Un usuario desactivado no puede iniciar sesión y cualquier sesión activa suya queda invalidada de inmediato.
- **Resetear Contraseña (`🔑 Reset Clave`):** Genera una nueva contraseña temporal de un solo uso y fuerza el cambio de contraseña en el siguiente inicio de sesión del usuario.
- **Reglas de protección del último administrador:** El sistema impide desactivar o quitar el rol `ADMIN` al último administrador activo para garantizar que la plataforma nunca quede huérfana de administración.

### 12.2. 📜 Catálogo de Certificaciones y Syllabus (`Administración → Certificaciones`)
- **Creación y Edición de Certificaciones:** Configuración completa de código único (`FHIR_R4`, `FHIR_R5`, etc.), versión de estándar, nombre descriptivo, nº de preguntas de simulación oficial, duración cronometrada en minutos, nota de corte oficial (%), URL base de la especificación para justificaciones y enlaces, color distintivo, icono/emoji y orden.
- **Módulos Habilitados (Feature Flags):** Activa o desactiva por certificación las herramientas disponibles (`Motor de Examen`, `Flashcards SM-2`, `Materiales & PDFBox`, `FHIR Search Builder`, `Validador HAPI FHIR`).
- **Gestión Dinámica de Áreas del Syllabus:** Permite añadir, editar y ordenar las áreas temáticas de cada certificación con su peso porcentual oficial (`weightPercent`). El sistema valida y muestra en tiempo real el balance acumulado (destacando si suma exactamente el 100% oficial).
- **Duplicación en un Clic (`📑 Duplicar`):** Clona una certificación existente junto con todas sus áreas y pesos del syllabus, creándola inicialmente en estado inactivo para permitir ajustes seguros de temario antes de ponerla a disposición de los alumnos.
- **Protección de Integridad Referencial:** No es posible eliminar certificaciones ni áreas que contengan preguntas o exámenes asociados en el histórico.

### 12.3. 📈 Resultados y Trazabilidad por Usuario (`Administración → Resultados Usuarios`)
- **Tabla de KPIs por usuario:** Muestra de un vistazo para cada cuenta en la certificación seleccionada: nº de exámenes completados, nota media, *Readiness Score*, preguntas dominadas en SM-2, fecha/nota del último intento y fecha del último acceso.
- **Inspección detallada de un usuario (`🔍 Ver Detalle`):**
  - Permite filtrar los datos del usuario por **modo de examen** (`SIMULATION`, `ADAPTIVE`, `TUTOR`, `ERRORS`) y por **rango de fechas** (`Desde` / `Hasta`).
  - Muestra sus tarjetas de KPIs (incluyendo actividad de Flashcards SM-2: tarjetas trabajadas, dominadas y pendientes hoy).
  - Renderiza el **Mapa de Calor por las áreas del Syllabus de la certificación activa** específico de ese alumno.
  - Lista su **Historial de Intentos** completo con botón **`🔍 Revisar`** para abrir el desglose pregunta a pregunta (opción marcada, respuesta correcta, tiempo empleado y explicación).
  - Lista las **Preguntas Más Falladas** por ese usuario con su tasa de error individual.
- **Exportación CSV:** Puedes descargar tanto el **CSV Global** de todos los intentos como el **CSV individual del usuario seleccionado** (respetando los filtros activos y la certificación).

### 12.4. 🌐 Analítica Global Agregada y Ranking (`Administración → Vista Agregada`)
- **Filtros combinables:** Filtra las métricas globales por usuario, modo de examen y rango de fechas, o exporta directamente los resultados filtrados a CSV.
- **KPIs Globales:** Usuarios activos, total de exámenes completados, nota media global, tasa de aprobados, *Readiness Score* medio y total de respuestas analizadas.
- **Precisión Media Global por Área del Syllabus:** Mapa de calor agregado de toda la organización en las áreas temáticas de la certificación activa.
- **Top Preguntas con Mayor Tasa de Fallo Global:** Identifica qué preguntas del banco están resultando más difíciles para el conjunto de alumnos.
- **Ranking y Comparativa de Preparación:** Clasificación ordenada por *Readiness Score* y nota media.

### 12.5. 📋 Registro de Auditoría (`Administración → Auditoría`)
- Muestra la bitácora inmutable de eventos críticos: inicios y cierres de sesión (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`), bloqueos por intentos fallidos (`ACCOUNT_LOCKED`), cambios de contraseña (`PASSWORD_CHANGED`), altas/ediciones/cambios de estado/reseteos de usuarios (`ADMIN_CREATE_USER`, `ADMIN_UPDATE_USER`, `ADMIN_CHANGE_USER_STATUS`, `ADMIN_RESET_PASSWORD`), operaciones de certificaciones y syllabus, y bajas lógicas de contenidos (`DELETE_QUESTION`, `DELETE_MATERIAL`), incluyendo fecha/hora, actor, entidad afectada, detalle e IP de origen.
