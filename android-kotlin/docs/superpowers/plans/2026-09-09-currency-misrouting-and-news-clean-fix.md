# Plan de Mejora: Corrección de Enrutamiento de Divisas, Limpieza de Noticias y Sobreescritura de Respaldo

## Contexto y Diagnóstico del Log (14:06 - 14:10)

Del análisis exhaustivo de `/home/rcastro/Descargas/myvu_client_log.txt`:

1. **TRM / Divisas**: Consulta `"¿Cuál es el TRM de hoy?"` ejecutada en **740ms** con éxito rotundo contra Superfinanciera (`1 USD = 3116.47 COP`).
2. **Resumen de Notificaciones**: Consulta `"Tengo notificaciones pendientes por revisar."` despachada en **14ms** sin timeouts ni invocación innecesaria al LLM.
3. **Falsa Clasificación de Definición como Divisa**:
   - Consulta: `"¿Cuál es el significado de la palabra retropropagación en redes neuronales?"`
   - Log: `VoiceActionRouter -> Fast-Path currency query: '¿Cuál es el significado de la palabra retropropagación en redes neuronales?'`
   - Causa raíz: En [`ExternalInfoService.isCurrencyQuery`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt#L53), la lista de palabras clave incluía `"eur"` y verificaba `currencyKeywords.any { norm.contains(it) }`.
   - La palabra `"neuronales"` contiene la subsecuencia `"eur"` (`n-EUR-onales`). Al no exigir límites de palabra (`\b`), cualquier consulta que mencione redes neuronales, neurociencia, etc., se clasificaba falsamente como consulta de tipo de cambio / divisas.
4. **Residuo de Prefijos y Preguntas en Búsqueda de Noticias**:
   - Consulta: `"¿Qué noticias relevantes hay hoy en Barranquilla?"`
   - Respuesta entregada: `"Noticias de hoy (¿Qué noticias en Barranquilla): 1) ..."`
   - Causa raíz: En [`ExternalInfoService.fetchNewsSearch`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt#L554), el regex no usaba bandera Unicode `(?iu)` para ignorar mayúsculas y acentos en caracteres como `"¿Qué"`, impidiendo que el patrón eliminara el prefijo interrogativo. Además, la limpieza de adjetivos temporales ocurría en pasos simples que dejaban preposiciones o restos sueltos en el tema de búsqueda y etiqueta.
5. **Fallo de Sobreescritura en Respaldo Público**:
   - Log: `BackupManager -> Could not copy to public Downloads: .../data.zip: Tried to overwrite the destination, but failed to delete it.`
   - Causa raíz: En [`BackupManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/BackupManager.kt#L202), `zipFile.copyTo(publicBackupFile, overwrite = true)` intenta ejecutar `destination.delete()` internamente, lo cual falla en Android Scoped Storage si el archivo existente en Descargas pertenece a otra sesión o está protegido. Sobrescribir mediante `FileOutputStream(publicBackupFile).use { ... }` permite truncar y sobreescribir los bytes directamente.

---

## Fases de Implementación

### Fase 1: Blindar Detección de Divisas con Límites de Palabra (`\b`)
- Archivo: [`ExternalInfoService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt)
- Reemplazar `contains(it)` plano por coincidencia con límites de palabra `Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE)`.
- Ignorar explícitamente consultas que sean definiciones claras (`significado`, `definicion`, `que es`, `concepto`).

### Fase 2: Robustecer Limpieza Iterativa de Noticias
- Archivo: [`ExternalInfoService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt)
- Incorporar `(?iu)` en las expresiones regulares para soporte completo de acentos (`¿Qué`, `cuáles`).
- Implementar bucle de purga secuencial para remover prefijos interrogativos (`¿qué noticias`, `cuáles son las noticias de`), stopwords conversacionales (`relevantes`, `hoy`, `hay en`), dejando únicamente el sujeto limpio (`"Barranquilla"`).

### Fase 3: Sobreescritura Segura de Respaldos
- Archivo: [`BackupManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/BackupManager.kt)
- Sustituir `copyTo(..., overwrite = true)` en directorio público de descargas por un stream `FileOutputStream` que trunca y actualiza el archivo sin depender de `delete()`.

### Fase 4: Pruebas Unitarias y Verificación
- Archivo: [`ExternalInfoServiceTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/ai/ExternalInfoServiceTest.kt)
- Agregar casos de prueba para:
  - `"¿Cuál es el significado de la palabra retropropagación en redes neuronales?"` -> `isCurrencyQuery == false`, `isGeneralSearchQuery == true`.
  - `"¿Qué noticias relevantes hay hoy en Barranquilla?"` -> Tema extraído limpio: `"Barranquilla"`.
  - Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

---

## Criterios de Éxito
1. Ninguna consulta conceptual con palabras como `"neuronales"`, `"girasoles"`, `"microprocesador"` entra a `isCurrencyQuery`.
2. Las consultas de noticias sobre ciudades o temas devuelven una etiqueta limpia sin prefijos como `"¿Qué noticias en "`.
3. El respaldo en Descargas públicas sobrescribe el archivo sin errores de permisos en Android.
4. Pasan el 100% de las pruebas unitarias.
