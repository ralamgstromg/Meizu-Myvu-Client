# Meizu MYVU AR Glasses — Android Client

Cliente Android de código abierto para gafas inteligentes de realidad aumentada **Meizu MYVU** (AR Smart Glasses) y auriculares compatibles. Proporciona enlace dual BLE/RFCOMM SPP, telemetría, HUD de navegación, dashboard, asistente de IA privado (Local AI / LiteLLM / Gemini Live), gestión de notas, recordatorios, control por gestos y rutinas diarias.

## Estructura del Repositorio

- [`android-kotlin/`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin): Código fuente de la aplicación Android nativa (Kotlin, Material 3 + Cupertino HIG, Room, Coroutines, Foreground Service).
  - [`android-kotlin/README.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/README.md): Documentación detallada del cliente móvil y características.
  - [`android-kotlin/docs/ARCHITECTURE.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/ARCHITECTURE.md): Arquitectura completa de capas, protocolos de comunicación BLE/SPP, TLV y pipeline de audio.
  - [`android-kotlin/docs/PROJECT_MEMORY.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/PROJECT_MEMORY.md): Memoria viva del proyecto, bitácora de decisiones, contexto de cambios, correcciones y estado técnico acumulado.
  - [`android-kotlin/docs/superpowers/plans/`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/superpowers/plans): Planes detallados de ejecución previa a implementaciones.
- [`graphify-out/`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/graphify-out): Mapeo estructural y grafo de dependencias generado por `graphify`.
- [`.codegraph/`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/.codegraph): Base de conocimiento e índices de símbolos indexados mediante `codegraph`.
- [`skills/`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/skills): Definición y catálogo de habilidades / skills del cliente.

## Reglas Maestras de Operación
Para consultar las reglas de desarrollo, convenciones de agentes, sincronización de `codegraph`, compresión de comandos vía `rtk` y protocolo de memoria viva, consultar [`AGENTS.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/AGENTS.md).
