# ADR 001 · Fan-out con presupuesto acotado
Estado: aceptada. Java 21, sin APIs preview.

Spring MVC acepta consultas de 1–12 proveedores ficticios. Admite hasta 8 consultas simultáneas; la novena recibe 429. Un executor crea un hilo virtual por proveedor y un semáforo global limita a 8 llamadas HTTP activas. La espera por ese permiso consume el deadline. HttpClient.send realiza I/O bloqueante sin reservar un hilo de plataforma por cada espera.

Cada consulta dispone de 1,5 segundos medidos con reloj monotónico. Recoge resultados parciales y cancela futuros pendientes con interrupción. Sin reintentos que amplifiquen carga. La API espera la agregación: no es fire-and-forget. El cliente no puede enviar URLs, evitando SSRF.

Para comparar, un executor de 8 hilos de plataforma tiene una cola acotada de 96 tareas. Ambos modos comparten el mismo presupuesto downstream. La admisión limita tareas virtuales también. El resultado no es un benchmark: los hilos virtuales no aceleran CPU ni servicios externos.

Un HttpClient reutilizable conserva su pool interno; connect timeout 300 ms y request timeout igual al presupuesto restante. El semáforo limita llamadas activas, no sockets. Se descarta el cuerpo del proveedor para mantener memoria acotada.

Micrometer registra latencia y resultados con etiquetas fijas. El correlation ID se genera en servidor y viaja al proveedor. Pruebas HTTP reales cubren ejecutores, errores, deadlines y saturación. Estado efímero y una instancia, sin autenticación; no usar con datos sensibles. En JDK 21 bloquear dentro de synchronized puede producir pinning; no hay monitores alrededor de I/O en este servicio. Investigar con JFR jdk.VirtualThreadPinned.

Fuente: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
