# 🧬 FHIR Cert Master — Plataforma Multiusuario de Preparación y Simulación HL7 FHIR R4

**FHIR Cert Master** es una aplicación web multiusuario diseñada para el estudio, práctica técnica, repetición espaciada (SuperMemo-2) y simulación de exámenes de certificación oficial **HL7 FHIR R4**, con trazabilidad completa por usuario y panel de analítica para administradores.

---

## 🛠️ Stack Tecnológico

- **Backend:** Java 21 + Spring Boot 3.3.5 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`)
- **Base de Datos y Migraciones:** SQLite 3 (`./data/fhir_exam.db` en modo `WAL` con `busy_timeout=5000`) + Hibernate 6 Community Dialects + **Flyway** (migraciones automáticas `V1`–`V7`)
- **Validación Clínica y Documental:** HAPI FHIR R4 (`hapi-fhir-structures-r4` 7.4.0) + Apache PDFBox 3.0.3
- **Frontend SPA:** HTML5 + Tailwind CSS + Vanilla JavaScript (`app.js`), Chart.js y Marked.js, servido estáticamente por Spring Boot (same-origin)
- **Seguridad:** Spring Security 6 con sesiones de servidor (`HttpOnly`, `SameSite=Lax`), protección CSRF mediante `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`), hash de contraseñas **BCrypt** (mínimo 10 caracteres), bloqueo temporal ante fuerza bruta y control de acceso por roles (`USER` y `ADMIN`) y propiedad de datos (anti-IDOR).

---

## ⚙️ Variables de Configuración (`app.conf` y Variables de Entorno)

La configuración operativa se gestiona en el archivo [`app.conf`](file:///c:/Area/Formacion/FHIR/AppExamenes/app.conf) en la raíz del proyecto (o mediante variables de entorno del sistema, que tienen prioridad sobre `app.conf`):

| Variable (`app.conf` / Entorno) | Propiedad Spring Boot | Valor por defecto | Descripción |
| :--- | :--- | :--- | :--- |
| `PORT` | `server.port` | `8787` (en `app.conf`) / `8080` | Puerto HTTP donde escucha el servidor embebido Tomcat. |
| `OPEN_BROWSER` | *(Script `start.ps1`)* | `true` | Abre automáticamente el navegador predeterminado al arrancar. |
| `JVM_MAX_MEMORY` | `-Xmx` | `1024m` | Memoria máxima asignada a la JVM. |
| `APP_ADMIN_USER` | `app.security.admin-username` | `admin` | Nombre de usuario del administrador creado en el bootstrap inicial. |
| `APP_ADMIN_PASSWORD` | `app.security.admin-password` | *(vacío)* | Contraseña inicial para el usuario admin en el primer arranque. Si se deja vacía, se genera una contraseña aleatoria segura, se muestra **una sola vez** en `logs/app.log` y se fuerza su cambio en el primer login (`mustChangePassword=true`). |
| `APP_MAX_FAILED_ATTEMPTS` | `app.security.max-failed-attempts` | `5` | Número máximo de intentos de login fallidos consecutivos antes de bloquear temporalmente la cuenta. |
| `APP_LOCK_DURATION_MINUTES` | `app.security.lock-duration-minutes` | `15` | Minutos que permanece bloqueada una cuenta tras superar `APP_MAX_FAILED_ATTEMPTS`. |
| `APP_COOKIE_SECURE` | `app.security.cookie-secure` | `false` | Marca las cookies de sesión (`FHIR_SESSION`) y CSRF (`XSRF-TOKEN`) con el flag `Secure` (activar a `true` si se despliega tras HTTPS). |
| `APP_SELF_REGISTRATION` | `app.security.self-registration` | `false` | Deshabilita el auto-registro público (solo un `ADMIN` puede dar de alta usuarios). |

---

## 🚀 Arranque y Operación (Windows 11)

| Script | Acción |
| :--- | :--- |
| `.\start.ps1` o `start.bat` | Compila (si no existe el JAR), aplica migraciones Flyway, inicializa el admin si no existe y arranca el servidor en segundo plano (`logs/app.log`). |
| `.\stop.ps1` o `stop.bat` | Detiene de forma limpia el proceso Java activo y libera el puerto. |
| `.\restart.ps1` o `restart.bat` | Reinicia el servicio leyendo los cambios de `app.conf`. |
| `.\status.ps1` o `status.bat` | Muestra si el servidor está activo, su PID, consumo de RAM y URL local. |

### Ejecución de Tests Automáticos
```powershell
Remove-Item env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue
Remove-Item env:LOGGING_CONFIG -ErrorAction SilentlyContinue
mvn test
```

---

## 🔐 Cómo Obtener o Recuperar la Contraseña de Administrador (`ADMIN`)

### Caso 1: Primer arranque (Bootstrap inicial)
1. Si `APP_ADMIN_PASSWORD` está vacío en `app.conf`, al iniciar la aplicación por primera vez verás en `logs/app.log` un bloque como este:
   ```text
   ========================================================================
     [BOOTSTRAP] CREDENCIALES INICIALES DE ADMINISTRADOR GENERADAS
     Usuario:    admin
     Contraseña: <contraseña-temporal-generada>
     NOTA: Esta contraseña se muestra UNA SOLA VEZ. Cámbiela al iniciar sesión.
   ========================================================================
   ```
2. Inicia sesión con esas credenciales; la aplicación te pedirá elegir una nueva contraseña personal (mínimo 10 caracteres).

### Caso 2: Pérdida u olvido de la contraseña del único Administrador
El inicializador de arranque (`AdminBootstrapInitializer`) crea automáticamente un administrador **cuando no existe ningún usuario con rol `ADMIN` y estado `active = 1`**.

Si has olvidado la contraseña del administrador y no tienes otra cuenta `ADMIN` activa, sigue cualquiera de estos dos métodos seguros sin perder ningún dato de exámenes ni trazabilidad:

#### Opción A (Recomendada): Re-disparar el bootstrap definiendo `APP_ADMIN_PASSWORD`
1. Detén la aplicación:
   ```powershell
   .\stop.ps1
   ```
2. Abre la base de datos SQLite `./data/fhir_exam.db` (con `sqlite3` o *DB Browser for SQLite*) y desactiva temporalmente el flag `active` del admin bloqueado/olvidado o bórralo solo si vas a recrear otro:
   - Mejor aún: para **conservar el mismo usuario `admin` y todos sus intentos históricos**, puedes actualizar directamente su estado o usar una cuenta admin de rescate temporal:
   - Define en tu terminal una cuenta admin de rescate temporal y arranca:
     ```sqlite
     UPDATE users SET active = 0 WHERE role = 'ADMIN';
     ```
   - A continuación, en PowerShell:
     ```powershell
     $env:APP_ADMIN_USER="admin_rescue"
     $env:APP_ADMIN_PASSWORD="RescateAdmin#2026"
     .\start.ps1
     ```
3. Al no detectar ningún `ADMIN` activo, el servidor creará el usuario `admin_rescue` con rol `ADMIN`.
4. Entra en la interfaz web con `admin_rescue` / `RescateAdmin#2026`, ve a **Administración → Usuarios**, reactiva tu usuario `admin` original y pulsa **`🔑 Reset Clave`** para obtener una contraseña temporal nueva.
5. Inicia sesión con tu cuenta `admin` original, cambia tu contraseña y desactiva `admin_rescue`.

#### Opción B: Actualizar directamente el hash BCrypt en SQLite
Si dispones de un hash BCrypt (coste 10) de una nueva contraseña de al menos 10 caracteres:
```sql
UPDATE users
SET password_hash = '<NUEVO_HASH_BCRYPT>',
    must_change_password = 1,
    failed_login_attempts = 0,
    locked_until = NULL,
    active = 1
WHERE username = 'admin';
```

---

## 📖 Documentación Adicional
Consulta el [**Manual de Usuario Detallado (`MANUAL_USUARIO.md`)**](file:///c:/Area/Formacion/FHIR/AppExamenes/MANUAL_USUARIO.md) para ver la guía completa de modos de examen, uso de fichas de estudio, modelo de propiedad de datos y vistas de administración.
