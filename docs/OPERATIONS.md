# Operación y diagnóstico

## Ejecutar varios proyectos juntos

Este repositorio usa 8086 (API), 4206 (Angular) y 9097 (Prometheus). Los otros repositorios pueden usar 8080/4200. `API_PORT` y `UI_PORT` permiten cambiar las publicaciones Docker sin modificar direcciones internas.

## Qué medir

En Prometheus:

```promql
sum(rate(quotes_results_total{status="SUCCESS"}[1m]))
sum(rate(quotes_results_total{status="TIMEOUT"}[1m]))
histogram_quantile(0.95, sum by (le,mode) (rate(quotes_batch_duration_seconds_bucket[5m])))
quotes_active_calls
```

Los contadores miden resultados de tareas y consultas, no pagos. Comparar modos por separado con los mismos proveedores, carga, deadline y concurrencia downstream. Un único clic no prueba una mejora de rendimiento. Cuando el proveedor es el cuello de botella, ambos modos pueden rendir igual.

## Java Flight Recorder

Para iniciar una grabación con el JDK 21 local:

```bash
java -XX:StartFlightRecording=filename=threads.jfr,duration=60s,settings=profile -jar target/virtual-thread-orchestrator-1.0.0.jar
jfr print --events jdk.VirtualThreadPinned threads.jfr
```

No observar eventos no demuestra ausencia absoluta de pinning: depende de la carga, duración y umbral de grabación. No versionar grabaciones con información sensible. El proyecto usa datos ficticios.

Documentación: [Oracle · Virtual Threads en JDK 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).

## Límites de la demo pública

GitHub Pages sirve Angular estático. El banner indica que el recorrido se simula en el navegador. No se contacta a la API, no hay hilos Java y no se presentan tiempos como mediciones reales. Para validar la implementación usa Maven, Docker Compose o la API local. Ninguna demo almacena información personal ni mueve dinero.
