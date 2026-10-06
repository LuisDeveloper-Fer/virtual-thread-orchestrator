# Entrevista
1. Virtual threads permiten muchas esperas bloqueantes sin reservar un hilo de plataforma por espera. No aceleran CPU.
2. El semáforo protege la capacidad finita del proveedor. Admisión y tamaño máximo acotan tareas pendientes.
3. La API espera la agregación; no es fire-and-forget. Un deadline común evita sumar timeouts por proveedor.
4. Cancelar con interrupción libera recursos locales; no garantiza detener trabajo remoto.
5. Comparar throughput bajo carga controlada, calentamiento y límites iguales. Un clic no es un benchmark.
6. En Java 21 estudiar pinning con JFR jdk.VirtualThreadPinned; evitar synchronized alrededor de I/O. No extrapolar a otros JDK.

https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
