# Validación

Fecha: 2026-10-06. Entorno local Windows, JDK 21.0.6, Maven 3.9.9, Node 22.

- Maven: 5 pruebas, 0 fallos, 0 errores; empaquetado correcto.
- Angular: compilación correcta.
- API local: ambos ejecutores devuelven seis respuestas correctas; Thread.isVirtual distingue los modos. ERROR devuelve HTTP_ERROR; SLOW y NEVER agotan el deadline.
- Docker Compose: sintaxis validada localmente. El motor local no está disponible; el stack completo se construyó y probó en GitHub Actions.
- [CI del código 420cabe](https://github.com/LuisDeveloper-Fer/virtual-thread-orchestrator/actions/runs/37418566091): backend, frontend y smoke del stack correctos.
- [Publicación de la demo](https://github.com/LuisDeveloper-Fer/virtual-thread-orchestrator/actions/runs/37418566107): correcta. La URL pública se abrió y el formulario devolvió los seis resultados simulados esperados.

Los commits posteriores a 420cabe incluyen documentación, captura pública, formato XML y exclusiones del contexto Docker; no modifican el comportamiento del backend o de la interfaz publicados. No se ha ejecutado un benchmark de rendimiento y no se afirma que virtual threads sean más rápidos.
