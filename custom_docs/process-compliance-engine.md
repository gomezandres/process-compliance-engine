
---

# Documento de Arquitectura: Monitoreo de Procesos con Evaluación por Configuración

## 1. Introducción y Contexto

El sistema actual tiene múltiples aplicaciones distribuidas en stacks tecnológicos heterogéneos que se ejecutan de forma programada en Kubernetes CronJobs o lambdas.

La necesidad evoluciona desde reportería hacia monitoreo de procesos. El reporte es un resultado final; lo importante es validar el ciclo de vida completo del proceso (inicio y fin) contra reglas de negocio y operación.

### Problema a resolver

- Dispersión: no existe un punto central para validar el inicio y cierre de procesos.
- Cobertura limitada: se observa el reporte final, pero no la gobernanza del proceso.
- Falta de control operativo: no hay evaluación consistente por reglas, SLA y destino esperado.
- Visibilidad parcial: sin el evento de inicio no es posible calcular duración real ni detectar procesos colgados.

---

## 2. Solución Propuesta (Simple)

Arquitectura simple y directa:

1. Un proceso inicia y publica un evento de inicio en RabbitMQ.
2. Un proceso termina y publica un evento de fin en RabbitMQ.
3. process-compliance-engine consume ambos tipos de eventos desde la misma queue (`process.event.lifecycle`).
4. process-compliance-engine consulta MySQL con la configuración del proceso.
5. process-compliance-engine evalúa reglas y envía alertas según el caso.

```mermaid
flowchart TD
    subgraph Apps["Procesos Productores"]
        P1["Proceso A"]
        P2["Proceso B"]
        PN["Proceso N"]
    end

    subgraph MQ["Mensajería"]
        RabbitMQ[["RabbitMQ\nprocess.event.lifecycle"]]
    end

    subgraph Core["Núcleo Central"]
        PSM["process-compliance-engine"]
    end

    subgraph Config["Configuración Operativa"]
        MySQL[("MySQL\nconfiguración de procesos")]
        Sync["Sincronizador\nplatform -> MySQL"]
    end

    subgraph Out["Salidas"]
        Registry[("Registro de ejecución")]
    end

    subgraph Observability["Observabilidad"]
        Prometheus[["Prometheus\n(scraping de métricas)"]]
        Grafana["Grafana\n(dashboards + alarmas)"]
    end

    Platform[["GitHub platform"]] --> Sync
    Sync --> MySQL

    P1 --> RabbitMQ
    P2 --> RabbitMQ
    PN --> RabbitMQ

    RabbitMQ --> PSM
    PSM --> MySQL
    PSM --> Registry
    PSM -- "expone /metrics" --> Prometheus
    Prometheus --> Grafana
```

---

## 3. Componentes

### A. Procesos Productores

Cada proceso publica un único evento cuando termina (éxito o fallo). No necesita conocer la lógica de monitoreo.

### B. RabbitMQ (`process.event.lifecycle`)

Canal único de recepción de eventos de ciclo de vida de proceso (inicio y fin).

- Desacopla productores del monitor central.
- Permite escalar sin cambiar contratos por aplicación.
- Routing keys: `process.lifecycle.started` para inicio, `process.lifecycle.completed` para fin.

### C. process-compliance-engine

Componente central que:

- Consume eventos de inicio y fin desde `process.event.lifecycle`.
- Registra el inicio de ejecución al recibir `PROCESS_STARTED`.
- Al recibir `PROCESS_COMPLETED`: busca configuración en MySQL, evalúa reglas y dispara alertas.
- Permite detectar procesos colgados (inicio sin fin dentro de una ventana de tiempo).
- **Expone métricas en el endpoint `/metrics`** en formato Prometheus (Micrometer o cliente equivalente).

### D. MySQL como fuente de información operativa

MySQL es la fuente que consulta process-compliance-engine en tiempo de ejecución.

- No se consulta platform de forma directa al evaluar eventos.
- La configuración llega a MySQL por sincronización automática o a demanda.

### E. Sincronizador platform -> MySQL

Servicio o job que mantiene MySQL actualizado desde platform.

- Modo automático: por schedule o webhook.
- Modo a demanda: ejecución manual/API cuando se requiera refrescar.

---

## 4. Contrato de eventos

Se reciben dos tipos de eventos publicados en la queue `process.event.lifecycle`.

### 4.1 Evento de inicio (`process.lifecycle.started`)

Campos requeridos:

- processKey
- executionId
- startedAt
- sourceSystem

Ejemplo:

```json
{
  "eventId": "uuid",
  "timestamp": "2026-06-19T08:02:30.000000Z",
  "source": "asiento-cero-hub",
  "type": "PROCESS_STARTED",
  "contextId": "daily-report-job",
  "payload": {
    "processKey": "asiento-cero-diario",
    "executionId": "exec-20260619-001",
    "status": "RUNNING",
    "scheduledAt": "2026-06-19T08:00:00",
    "startedAt": "2026-06-19T08:02:30",
    "metadata": {}
  }
}
```

### 4.2 Evento de fin (`process.lifecycle.completed`)

Campos requeridos:

- processKey
- executionId
- status (`COMPLETED` o `FAILED`)

Campos opcionales:

- scheduledAt, startedAt, endedAt
- durationMs
- errorCode, errorMessage
- metadata

Ejemplo:

```json
{
  "eventId": "uuid",
  "timestamp": "2026-06-19T08:15:45.000000Z",
  "source": "asiento-cero-hub",
  "type": "PROCESS_COMPLETED",
  "contextId": "daily-report-job",
  "payload": {
    "processKey": "asiento-cero-diario",
    "executionId": "exec-20260619-001",
    "status": "COMPLETED",
    "scheduledAt": "2026-06-19T08:00:00",
    "startedAt": "2026-06-19T08:02:30",
    "endedAt": "2026-06-19T08:15:45",
    "durationMs": 789000,
    "errorCode": null,
    "errorMessage": null,
    "metadata": {}
  }
}
```

### 4.3 Comportamiento del motor por tipo de evento

| Tipo de evento | Acción del motor |
|---|---|
| `PROCESS_STARTED` | Registra inicio de ejecución. No evalúa reglas aún. Permite detectar procesos colgados (sin evento de fin). |
| `PROCESS_COMPLETED` | Completa el registro de ejecución y evalúa todas las reglas configuradas. Genera alertas si hay violaciones. |

---

## 5. Métricas expuestas

process-compliance-engine expone métricas en formato Prometheus a través del endpoint `/metrics`.
Prometheus realiza scraping periódico de ese endpoint. Grafana consume Prometheus como datasource y sobre esas métricas se configuran los dashboards y las alarmas.

### Métricas recomendadas

| Métrica | Tipo | Descripción |
|---|---|---|
| `pce_events_received_total` | Counter | Total de eventos recibidos (etiqueta: `type=started\|completed`) |
| `pce_events_evaluated_total` | Counter | Total de eventos de fin evaluados (etiqueta: `result=ok\|fail`) |
| `pce_events_unknown_process_total` | Counter | Eventos de procesos sin configuración en MySQL |
| `pce_executions_hung_total` | Counter | Procesos con inicio registrado pero sin fin dentro de la ventana esperada |
| `pce_rule_violations_total` | Counter | Violaciones de reglas (etiqueta: `rule_type`, `severity`) |
| `pce_sla_exceeded_total` | Counter | Ejecuciones que superaron el SLA configurado |
| `pce_evaluation_duration_seconds` | Histogram | Tiempo de evaluación por evento |
| `pce_mysql_query_duration_seconds` | Histogram | Tiempo de consulta a MySQL por evaluación |

### Alarmas configuradas en Grafana

Las alarmas se definen en Grafana sobre queries PromQL ejecutadas contra Prometheus:

- **Alta tasa de fallos**: `rate(pce_events_evaluated_total{result="fail"}[5m]) > umbral`
- **SLA excedido sostenido**: `increase(pce_sla_exceeded_total[15m]) > 0`
- **Proceso sin configuración**: `increase(pce_events_unknown_process_total[5m]) > 0`
- **Violación crítica**: `increase(pce_rule_violations_total{severity="CRITICAL"}[5m]) > 0`
- **Latencia de evaluación alta**: `histogram_quantile(0.95, pce_evaluation_duration_seconds) > 2`

---

## 6. Reglas de evaluación en process-compliance-engine

Reglas mínimas sugeridas (se evalúan al recibir `PROCESS_COMPLETED`):

1. Proceso no configurado en MySQL: alerta de configuración.
2. Estado `FAILED`: alerta crítica.
3. SLA excedido: alerta de cumplimiento.
4. Evento con contrato incompleto o inválido: alerta de integridad de contrato.
5. Evento duplicado por `executionId`: deduplicar y registrar.
6. Proceso colgado: inicio registrado sin evento de fin dentro de la ventana esperada (detectado por job periódico, no en tiempo real).

Nota de alcance:

- La validación de entrega/destino final del artefacto (S3, SFTP, email) no es responsabilidad de esta aplicación.
- Esa validación debe ejecutarse en el productor o en un servicio dedicado de auditoría de entrega.

---

## 6. Modelo mínimo en MySQL

Tablas base recomendadas:

- `process_definition`
  - process_key
  - active
  - sla_minutes
  - owner
  - severity

- `process_rule`
  - process_key
  - rule_type
  - threshold
  - enabled

- `process_execution`
  - execution_id
  - process_key
  - status
  - finished_at
  - duration_ms
  - payload_json
  - evaluation_result

- `config_sync_audit`
  - source
  - synced_at
  - sync_mode (`AUTO` / `ON_DEMAND`)
  - result

---

## 7. Estrategia de alertamiento

El sistema combina dos mecanismos complementarios de alarmas:

### Alertas de negocio (desde process-compliance-engine)

Disparadas directamente por la evaluación de reglas, en tiempo real por cada evento procesado.

Severidades:

- INFO
- WARNING
- CRITICAL

Canales:

- Email
- Slack
- Pager (opcional para procesos críticos)

### Alarmas operativas (desde Grafana sobre métricas Prometheus)

Disparadas por condiciones observadas en las métricas del sistema. Permiten detectar degradación, acumulación de fallos o silencio anómalo de procesos que no depende de un único evento.

Canales de notificación configurados en Grafana:

- Slack (canal de operaciones)
- Email (on-call / equipo de turno)
- PagerDuty (procesos críticos)

Buenas prácticas:

- Deduplicar por `executionId`.
- Aplicar ventana de enfriamiento para evitar spam de alertas repetidas.

---

## 8. Beneficios

- Simplicidad operativa: un único evento de fin por proceso.
- Evaluación consistente: reglas centralizadas contra configuración en MySQL.
- Desacoplamiento: productores solo publican evento; monitoreo central decide.
- Evolución controlada: platform sigue siendo origen de configuración, MySQL es fuente operativa en runtime.
- Observabilidad integrada: métricas expuestas en Prometheus permiten configurar dashboards y alarmas en Grafana sin instrumentación adicional en los productores.

En resumen, se pasa de monitoreo de reportes a monitoreo de procesos con una implementación simple, gobernable y escalable.