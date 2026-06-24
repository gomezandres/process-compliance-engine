# Especificacion de Base de Datos - process-compliance-engine

## 1. Objetivo

Esta especificacion define el modelo minimo de base de datos para evaluar cumplimiento de procesos.

Reglas operativas obligatorias:

- Si una regla existe para un proceso y `enabled = 1`, la regla se debe validar.
- No se usa vigencia temporal por regla (`valid_from` o `valid_to`).
- `process_execution` registra el ciclo de vida completo: el evento `PROCESS_STARTED` crea el registro con `status=RUNNING`; el evento `PROCESS_COMPLETED` actualiza el registro y dispara la evaluacion de reglas.
- La evaluacion de reglas ocurre **unicamente** al recibir el evento de fin (`PROCESS_COMPLETED`).
- Si llega un evento de fin sin registro previo de inicio, el motor crea el registro completo y evalua de igual forma.

---

## 2. Tablas

Para el MVP se mantienen estas tablas: `process_definition`, `process_rule` y `process_execution`.

### 2.1 process_definition

Define cada proceso monitoreado.

```sql
CREATE TABLE process_definition (
  process_key VARCHAR(120) PRIMARY KEY,
  active TINYINT(1) NOT NULL DEFAULT 1,
  schedule_cron VARCHAR(80) NOT NULL,
  schedule_timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
  schedule_grace_minutes INT NOT NULL DEFAULT 10,
  owner VARCHAR(120) NULL,
  description VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 2.2 process_rule

Define reglas por proceso. Si la regla existe y `enabled = 1`, se evalua.

```sql
CREATE TABLE process_rule (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  process_key VARCHAR(120) NOT NULL,
  rule_type VARCHAR(60) NOT NULL,
  operator VARCHAR(20) NOT NULL,

  value_type VARCHAR(20) NOT NULL, -- NUMBER, STRING, BOOLEAN, JSON
  threshold_number DECIMAL(18,6) NULL,
  threshold_string VARCHAR(255) NULL,
  threshold_boolean TINYINT(1) NULL,
  threshold_json JSON NULL,

  severity ENUM('INFO','WARNING','CRITICAL') NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  description VARCHAR(300) NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT chk_value_type CHECK (value_type IN ('NUMBER','STRING','BOOLEAN','JSON')),
  INDEX idx_rule_process_enabled (process_key, enabled),

  CONSTRAINT fk_rule_process
    FOREIGN KEY (process_key) REFERENCES process_definition(process_key)
    ON DELETE CASCADE
);
```

### 2.3 process_execution

Registra el ciclo de vida completo de cada ejecucion. El registro se crea con `status=RUNNING` al recibir el evento de inicio y se actualiza al recibir el evento de fin.

Columnas de fin (`ended_at`, `duration_ms`, `error_code`, `error_message`) permanecen `NULL` mientras la ejecucion esta en curso.

```sql
CREATE TABLE process_execution (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  execution_id VARCHAR(120) NOT NULL,
  process_key VARCHAR(120) NOT NULL,

  scheduled_at DATETIME NULL,
  started_at   DATETIME NULL,
  ended_at     DATETIME NULL,        -- NULL hasta recibir PROCESS_COMPLETED
  duration_ms  BIGINT UNSIGNED NULL, -- NULL hasta recibir PROCESS_COMPLETED

  status ENUM('RUNNING','COMPLETED','FAILED','SKIPPED','UNKNOWN') NOT NULL DEFAULT 'RUNNING',
  error_code    VARCHAR(60)  NULL,   -- NULL hasta recibir PROCESS_COMPLETED
  error_message VARCHAR(500) NULL,   -- NULL hasta recibir PROCESS_COMPLETED

  metadata   JSON     NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT chk_status CHECK (status IN ('RUNNING','COMPLETED','FAILED','SKIPPED','UNKNOWN')),
  INDEX idx_execution_process (process_key),
  INDEX idx_execution_id      (execution_id),
  INDEX idx_execution_created (process_key, created_at),
  INDEX idx_execution_running (process_key, status, started_at), -- para detectar procesos colgados
  UNIQUE INDEX idx_execution_unique (process_key, execution_id),

  CONSTRAINT fk_execution_process
    FOREIGN KEY (process_key) REFERENCES process_definition(process_key)
    ON DELETE CASCADE
);
```

---

## 3. Catalogo sugerido de rule_type

- `SLA_MAX_MINUTES`
- `FAIL_STATUS`
- `DUPLICATE_EXECUTION`
- `ALLOWED_ERROR_CODES`
- `MAX_DURATION_MS`
- `MISSED_SCHEDULE`

### 3.1 Detalle de cada rule_type

| rule_type | Que valida | Dato de entrada esperado | Configuracion sugerida |
|---|---|---|---|
| `SLA_MAX_MINUTES` | Que la ejecucion no exceda el SLA en minutos | Duracion de ejecucion en minutos (o `durationMs` convertido) | `value_type=NUMBER`, `operator=GT`, `threshold_number=30`, `severity=CRITICAL` |
| `FAIL_STATUS` | Que el estado de finalizacion no sea fallido | `status` del evento de cierre | `value_type=STRING`, `operator=EQ`, `threshold_string=FAILED`, `severity=CRITICAL` |
| `DUPLICATE_EXECUTION` | Que no exista repeticion de `execution_id` para el mismo proceso | `execution_id` del evento + verificacion en almacenamiento de deduplicacion | `value_type=BOOLEAN`, `operator=EQ`, `threshold_boolean=1`, `severity=WARNING` |
| `ALLOWED_ERROR_CODES` | Que un error reportado pertenezca al conjunto permitido | `errorCode` del evento (cuando aplica) | `value_type=JSON`, `operator=IN`, `threshold_json=[...]`, `severity=WARNING` |
| `MAX_DURATION_MS` | Que la duracion no supere un maximo en milisegundos | `durationMs` del evento | `value_type=NUMBER`, `operator=GT`, `threshold_number=1800000`, `severity=CRITICAL` |
| `MISSED_SCHEDULE` | Que el proceso se ejecute dentro de su ventana esperada de schedule | Hora esperada (`schedule_cron` + `schedule_timezone`) y ultimo evento recibido | `value_type=NUMBER`, `operator=GT`, `threshold_number=schedule_grace_minutes`, `severity=WARNING` |

Notas de interpretacion:

- Para reglas de tiempo (`SLA_MAX_MINUTES`, `MAX_DURATION_MS`), un `GT` indica incumplimiento cuando el valor observado supera el umbral.
- `MISSED_SCHEDULE` se evalua comparando el retraso actual contra `schedule_grace_minutes`.
- `DUPLICATE_EXECUTION` depende de una estrategia de deduplicacion persistente para detectar repetidos.

### 3.2 Operadores soportados

Los siguientes operadores se usan en la columna `operator` de `process_rule`:

| Operador | Se lee como | Equivalente |
|---|---|---|
| `GT` | greater than | `>` |
| `GTE` | greater than or equal | `>=` |
| `LT` | lower than | `<` |
| `LTE` | lower than or equal | `<=` |
| `EQ` | equal | `=` |
| `NEQ` | not equal | `!=` |
| `IN` | in collection | `IN (...)` |

---

## 4. Ejemplos de insercion

Nota:

- Los INSERT de `process_rule` incluyen solo los campos requeridos + el threshold que corresponde al `value_type`.
- `id` no se inserta porque es `AUTO_INCREMENT`.
- `created_at` no se inserta porque usa `DEFAULT CURRENT_TIMESTAMP`.
- Los thresholds no usados en cada regla se dejan en `NULL`.

```sql
INSERT INTO process_definition (process_key, active, schedule_cron, schedule_timezone, schedule_grace_minutes, owner)
VALUES
('asiento-cero-diario', 1, '0 8 * * 1-5', 'America/Santiago', 10, 'finops-team');
```

```sql
-- Regla numerica
INSERT INTO process_rule
(process_key, rule_type, operator, value_type, threshold_number, severity, enabled, description)
VALUES
('asiento-cero-diario', 'SLA_MAX_MINUTES', 'GT', 'NUMBER', 30, 'CRITICAL', 1, 'Maximo SLA en minutos');
```

```sql
-- Regla string
INSERT INTO process_rule
(process_key, rule_type, operator, value_type, threshold_string, severity, enabled, description)
VALUES
('asiento-cero-diario', 'FAIL_STATUS', 'EQ', 'STRING', 'FAILED', 'CRITICAL', 1, 'Estado de fallo');
```

```sql
-- Regla boolean
INSERT INTO process_rule
(process_key, rule_type, operator, value_type, threshold_boolean, severity, enabled, description)
VALUES
('asiento-cero-diario', 'DUPLICATE_EXECUTION', 'EQ', 'BOOLEAN', 1, 'WARNING', 1, 'Duplicado por execution_id');
```

```sql
-- Regla JSON
INSERT INTO process_rule
(process_key, rule_type, operator, value_type, threshold_json, severity, enabled, description)
VALUES
('asiento-cero-diario', 'ALLOWED_ERROR_CODES', 'IN', 'JSON', JSON_ARRAY('E101','E205'), 'WARNING', 1, 'Codigos permitidos');
```

```sql
-- Paso 1: registro creado por evento PROCESS_STARTED (columnas de fin son NULL)
INSERT INTO process_execution
(execution_id, process_key, scheduled_at, started_at, status)
VALUES
('exec-20240115-001', 'asiento-cero-diario', '2024-01-15 08:00:00', '2024-01-15 08:02:30', 'RUNNING');
```

```sql
-- Paso 2: actualizacion por evento PROCESS_COMPLETED (dispara evaluacion de reglas)
UPDATE process_execution
SET ended_at    = '2024-01-15 08:15:45',
    duration_ms = 789000,
    status      = 'COMPLETED'
WHERE process_key  = 'asiento-cero-diario'
  AND execution_id = 'exec-20240115-001';
```

```sql
-- Alternativa: registro completo cuando llega PROCESS_COMPLETED sin inicio previo
INSERT INTO process_execution
(execution_id, process_key, scheduled_at, started_at, ended_at, duration_ms, status)
VALUES
('exec-20240115-001', 'asiento-cero-diario', '2024-01-15 08:00:00', '2024-01-15 08:02:30', '2024-01-15 08:15:45', 789000, 'COMPLETED');
```

```sql
-- Registro de ejecucion fallida con codigo de error
UPDATE process_execution
SET ended_at      = '2024-01-15 09:03:22',
    duration_ms   = 127000,
    status        = 'FAILED',
    error_code    = 'E101',
    error_message = 'Connection timeout to data source'
WHERE process_key  = 'asiento-cero-diario'
  AND execution_id = 'exec-20240115-002';
```

---

## 5. Consulta de reglas activas para evaluar

No hay vigencia temporal. Solo se evalua por `enabled = 1`.

```sql
SELECT *
FROM process_rule
WHERE process_key = ?
  AND enabled = 1
ORDER BY id ASC;
```

### 5.1 Consulta de ultima ejecucion completada por proceso

Para validar si un proceso se ejecuto recientemente (usado por regla `MISSED_SCHEDULE`):

```sql
SELECT *
FROM process_execution
WHERE process_key = ?
  AND status IN ('COMPLETED', 'FAILED')
ORDER BY ended_at DESC
LIMIT 1;
```

### 5.2 Consulta de ejecuciones en ventana de tiempo

Para analizar patrones de incumplimiento:

```sql
SELECT *
FROM process_execution
WHERE process_key = ?
  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY created_at DESC;
```

### 5.3 Consulta de procesos colgados

Ejecuciones que llevan mas tiempo del SLA esperado sin recibir evento de fin.
Usada por un job periodico para detectar procesos que iniciaron pero nunca completaron.

```sql
SELECT pe.execution_id,
       pe.process_key,
       pe.started_at,
       TIMESTAMPDIFF(MINUTE, pe.started_at, NOW()) AS minutes_running,
       pd.schedule_grace_minutes
FROM process_execution pe
JOIN process_definition pd ON pd.process_key = pe.process_key
WHERE pe.status = 'RUNNING'
  AND pe.started_at < DATE_SUB(NOW(), INTERVAL pd.schedule_grace_minutes MINUTE)
ORDER BY pe.started_at ASC;
```

---

## 6. Reglas de evaluacion (comportamiento esperado)

### 6.1 Evento PROCESS_STARTED

- El motor crea un registro en `process_execution` con `status=RUNNING`.
- Si ya existe un registro para `(process_key, execution_id)`, el evento se descarta (duplicado).
- No se evaluan reglas en este punto.

### 6.2 Evento PROCESS_COMPLETED

- Si no existe registro previo de inicio, el motor crea el registro completo antes de evaluar.
- Si llega un evento con `process_key` sin registro en `process_definition`, el motor genera alerta de configuracion (severidad recomendada: `WARNING` o `CRITICAL` segun impacto).
- Si no existen reglas activas para el proceso, el motor registra `PASS` por ausencia de reglas.
- Si existen reglas activas, el motor evalua **todas** las reglas habilitadas.
- Si una o mas reglas fallan, el resultado global es `FAIL` y se generan alertas segun severidad.
- Si todas pasan, el resultado global es `PASS`.

### 6.3 Deteccion de procesos colgados

- Un job periodico consulta `process_execution` buscando registros con `status=RUNNING` cuyo `started_at` supere la ventana `schedule_grace_minutes` de la definicion.
- Estos registros se marcan con `status=UNKNOWN` y se genera alerta de severidad `WARNING`.
- Esta deteccion opera fuera del flujo de eventos, a demanda del scheduler.

---

## 7. Recomendaciones operativas

- Mantener un solo campo threshold poblado segun `value_type`.
- Auditar cambios de reglas (quien cambio, que cambio, cuando).
- Evitar reglas duplicadas por (`process_key`, `rule_type`, `enabled`) cuando aplique al dominio.
- Definir `schedule_cron` y `schedule_timezone` por proceso para poder detectar ejecuciones omitidas.
- Usar `schedule_grace_minutes` para evitar falsos positivos por retrasos menores del orquestador.
- El indice `idx_execution_running (process_key, status, started_at)` es critico para la consulta de procesos colgados; no eliminarlo.
- Hacer purga periodica de registros con `status=RUNNING` muy antiguos (>24hs) que nunca recibieron evento de fin, marcandolos como `UNKNOWN`.
