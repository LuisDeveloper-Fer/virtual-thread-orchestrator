# Contrato HTTP
POST /api/quotes · JSON

Request: {"mode":"VIRTUAL","scenario":"SUCCESS","providers":3}

mode: VIRTUAL | PLATFORM. scenario: SUCCESS | SLOW | ERROR | NEVER. providers: 1–12.

200: {"correlationId":"uuid","mode":"VIRTUAL","elapsedMs":120,"results":[{"provider":0,"status":"SUCCESS","virtualThread":true}]} (ejemplo abreviado, tiempos ilustrativos).

X-Correlation-ID también en la respuesta. Errores del proveedor son resultados parciales dentro del 200: SUCCESS, HTTP_ERROR, TIMEOUT, IO_ERROR, CANCELLED. virtualThread viene de Thread.isVirtual(); null significa que el coordinador venció el plazo sin recibir el resultado de la tarea.

400 application/problem+json: validación. 429 application/problem+json con Retry-After: 2: capacidad agotada. Sin reintentos automáticos.

GET /actuator/health y /actuator/prometheus para operación local.
