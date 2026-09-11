# Plan de Implementación: Búsqueda Priorizada de Contactos por Orden Secuencial y Fallback Semántico

## Contexto y Diagnóstico
En comandos de voz naturales como `"Enviar mensaje de whatsapp a Matias Castro, hola hijo"`, el parser extrae correctamente al destinatario (`"Matias Castro"`). Sin embargo, el motor de búsqueda en `ContactHelper.kt` calculaba la similitud mediante una sumatoria laxa de tokens donde:
- El contacto `"Denis Castro"` compartía el apellido `"Castro"`.
- Se le asignaban 60 puntos por coincidencia de `"Castro"` + 100 puntos por cobertura de token parcial (`matchedSTokens = 1 / 2`) + 50 puntos de bonificación por número colombiano (+57).
- Total = 210 puntos ($\ge 30$), lo que provocaba que se seleccionara `"Denis Castro"` en lugar de buscar ordenadamente a `"Matias Castro"` o descartar el contacto si el nombre de pila no coincidía en lo absoluto.

## Objetivos
1. **Prioridad Estricta al Orden Secuencial**:
   - Fase 1 (Prioridad 1): Evaluar coincidencia exacta completa, coincidencia de prefijo secuencial y coincidencia de tokens en el orden estricto de entrada ($Q_0 \rightarrow Q_1 \rightarrow \dots$).
   - Anclaje del Nombre de Pila: En consultas compuestas (nombre + apellido), el primer token ($Q_0$, ej: `"matias"`) DEBE coincidir con el inicio o primer token del contacto.
2. **Protección Contra Falsos Positivos por Apellido**:
   - Si la consulta tiene múltiples tokens y el primer token ($Q_0$) no coincide en absoluto (nombre de pila distinto como `"Denis"` vs `"Matias"`), el contacto recibe puntaje 0 y es descartado inmediatamente.
3. **Búsqueda Semántica / Difusa como Fallback (Fase 2)**:
   - Si y solo si no existe ninguna coincidencia secuencial en Fase 1, se activa la Fase 2:
     - Diccionario de alias y diminutivos comunes en español (`"mati"` $\leftrightarrow$ `"matias"`, `"dani"` $\leftrightarrow$ `"daniel"/"daniela"`, `"sebas"` $\leftrightarrow$ `"sebastian"`, `"santi"` $\leftrightarrow$ `"santiago"`, `"juanca"` $\leftrightarrow$ `"juan carlos"`, etc.).
     - Relaciones semánticas y parentescos (`"hijo"`, `"papa"`, `"mama"`, `"esposa"`, etc.).
     - Coincidencias no ordenadas con 100% de cobertura (ej: `"Castro Matias"` para `"Matias Castro"`).
     - Distancia Levenshtein / fonética tolerante para errores ortográficos leves.
4. **Búsqueda en Dos Fases en Proveedor de Contactos**:
   - `findBestContactMatch` y métodos auxiliares de WhatsApp (`resolveWhatsAppChatDataId`, `resolveWhatsAppVoipDataId`) darán precedencia absoluta a candidatos de Fase 1 sobre cualquier fallback de Fase 2.
5. **Pruebas Unitarias Exhaustivas**:
   - Validar que `"Matias Castro"` jamás coincida con `"Denis Castro"`.
   - Validar que `"Matias Castro"` elija `"Matias Castro"`, `"Matias Castro Hijo"`, o `"Matias"` antes que cualquier contacto ajeno.
   - Validar que consultas de un solo token como `"Castro"` sí coincidan con contactos que lleven `"Castro"`.
   - Validar fallback semántico y alias (`"Mati"` para `"Matias"`, `"Castro Matias"` para `"Matias Castro"`).

---

## Tareas Detalladas

### Tarea 1: Mejorar Algoritmo de Scoring y Semántica en `ContactHelper.kt`
- Implementar diccionario de alias semánticos en español (`SEMANTIC_ALIASES`).
- Implementar función `isTokenMatch(qToken, cToken)` con soporte para exacto, prefijo, Levenshtein ($\le 1$) y alias semántico.
- Reestructurar `calculateScore(searchQuery, contactName)`:
  - **Tier 1 (Exacto)**: 3000 pts.
  - **Tier 2 (Prefijo continuo)**: 2000 pts.
  - **Tier 3 (Orden Secuencial Estricto con anclaje en primer token)**: 1200 - 1600 pts.
  - **Regla de Seguridad**: Si $Q$ tiene $\ge 2$ tokens y el primer token no coincide en absoluto con ningún token del contacto, retornar 0 pts.
  - **Tier 4 (Fallback Semántico / Desordenado)**: 200 - 600 pts (solo si pasa la regla de seguridad o si todos los tokens están presentes en orden invertido).

### Tarea 2: Actualizar `findBestContactMatch`, `resolveWhatsAppChatDataId` y `resolveWhatsAppVoipDataId`
- Asegurar que la selección de contactos respete los rangos de puntaje y el umbral mínimo de seguridad ($\ge 150$).
- La bonificación por número colombiano (+50) solo desempatará candidatos del mismo rango cualitativo.

### Tarea 3: Pruebas Unitarias en `ContactHelperTest.kt`
- Agregar tests unitarios:
  - `testMatiasCastroNeverMatchesDenisCastro()`
  - `testSequentialOrderPriorityBeatsUnorderedOrPartial()`
  - `testSingleTokenSurnameQueryStillWorks()`
  - `testSemanticAliasesResolveCorrectly()`
  - `testCompoundNameSequentialMatch()`

### Tarea 4: Verificación y Compilación
- Ejecutar `./gradlew testDebugUnitTest` con `rtk`.
- Ejecutar `rtk codegraph sync`.
- Actualizar `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
