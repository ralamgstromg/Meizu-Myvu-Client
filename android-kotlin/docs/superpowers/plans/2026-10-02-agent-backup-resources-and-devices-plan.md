# Plan de Implementación: Backup/Restore v2, Gestión de Recursos y Mejoras HUD/Audífonos

**Fecha:** 2026-10-02
**Módulo:** `core/BackupManager.kt`, `database/AppDatabase.kt`, `ai/AiConversation.kt`, `ai/MeetingAiProcessor.kt`, `ui/SettingsActivity.kt`
**Estado:** Fases 1 y 2 implementadas. La fase 3 son propuestas pendientes de aprobación.

---

## 1. Diagnóstico

### 1.1 Backup / Restore (`core/BackupManager.kt`, 440 líneas)

| # | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| B1 | **Alta (seguridad)** | `preferences.json` exporta **todas** las SharedPreferences por defecto, incluidos `gdrive_refresh_token`, `gdrive_access_token` y `gdrive_client_secret`, en texto plano. El zip se copia a `Download/MYVU/` (público) y se sube a Drive. Un refresh token da acceso persistente a la cuenta de Drive. | `BackupManager.kt` L111–129; `GoogleDriveSyncHelper.kt` L48–54, L154–163 |
| B2 | **Alta (corrupción)** | Restore cierra `LocalDatabase` pero **no** la base Room `myvu_chat.db` (`AppDatabase`). El archivo se sobrescribe con la conexión abierta. | `BackupManager.kt` L295–304; `AppDatabase` no tiene `closeInstance()` |
| B3 | Alta | Restore no es atómico ni reversible. Si falla a mitad (por ejemplo, después de copiar la DB y al procesar preferencias), los datos quedan mezclados. No hay copia de seguridad previa. | `restoreBackup()` |
| B4 | Media | El manifiesto **no tiene checksums** (el KDoc dice que sí). Un zip truncado o alterado se restaura igual. No se valida `db_version`: restaurar un respaldo de una versión más nueva de la app rompe `onUpgrade`/`onDowngrade`. | L180–191, L267–271 |
| B5 | Media | Protección Zip Slip incompleta: `startsWith(canonicalPath)` sin separador acepta `restore_temp_123evil/…`. Tampoco hay límite de tamaño (zip bomb). | L418 |
| B6 | Media | Los medios se aplanan por nombre: archivos con el mismo nombre en carpetas distintas se pierden. Al restaurar, la carpeta se elige por extensión, así que los adjuntos de `filesDir/audio` o de la raíz se reubican y las rutas guardadas en la DB dejan de existir. | L150–164, L345–355 |
| B7 | Media | Cada respaldo sobrescribe `data.zip`: no hay historial. En Downloads, MediaStore crea `data (1).zip`, `data (2).zip`… sin rotación. | L196–232 |
| B8 | Baja | Se copia todo a una carpeta temporal antes de comprimir (doble espacio en disco). `app_version` está fijo en `"0.3"`. | L76–77, L181 |
| B9 | Baja | El diálogo dice "las claves de IA han sido actualizadas", pero las claves viven en `SecurePrefs` (Keystore) y nunca se respaldan. | `SettingsActivity.kt` L928 |
| B11 | **Alta (pérdida de datos)** | `PRAGMA wal_checkpoint` se ejecutaba con `rawQuery(...).close()` sin leer el cursor. Los cursores son lazy, así que el checkpoint **nunca corría**: lo que estaba en el WAL (en Room, incluso el esquema) no entraba al respaldo. Lo detectó el test de ida y vuelta del chat. | L86, L100 |
| B10 | Baja | Después de restaurar, la app sigue con singletons y caches viejos (repositorios, Flows de Room). Hace falta reiniciar el proceso. | `showRestoreSuccessDialog` |

### 1.2 Recursos, hilos y errores

| # | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| R1 | **Alta (ANR)** | `AiConversation.deliver()` corre en el **hilo principal** (`main.post { deliver(...) }`) y ejecuta `SkillExecutor.processAndExecute` con `runBlocking`. Las skills hacen red (búsqueda, clima, divisas): bloquean la UI durante segundos y pueden causar ANR. | `AiConversation.kt` L636, L651 |
| R2 | Media (fuga) | `MeetingAiProcessor` crea un `newSingleThreadExecutor()` que nunca se apaga y guarda la `Activity` como `Context`. Cada vez que se abre `VoiceRecorderActivity` o `RecordingDetailActivity` se fuga un hilo, y potencialmente la Activity. | `MeetingAiProcessor.kt` L17–21 |
| R3 | Baja | `runBlocking` sobre Room desde el hilo principal (`isActiveListeningEnabledBlocking`) en `AiConversation` L256/L803 y `ConnectionManager` L1046. | `BluetoothDeviceManager.kt` L418 |
| R4 | Baja | 51 `catch (ignored: Exception)` y 36 bloques catch vacíos. Ocultan fallos reales (por ejemplo, el envío de notificaciones al HUD). | grep |
| R5 | Info | Coroutines sin scope (`CoroutineScope(Dispatchers.IO).launch`) en `NoteAiProcessor` (4) y `TotalDisconnectHelper`. Tienen try/catch, así que no hacen crash, pero no son cancelables. | |

### 1.3 HUD y audífonos (propuestas)

| # | Propuesta | Valor |
|---|---|---|
| D1 | ~~Modo de respuesta automático por dispositivo~~ **Descartada tras el análisis**: con solo audífonos, `HeadphoneGestureManager` abre `ChatActivity` con STT automático y responde por TTS; nunca pasa por el HUD. La instancia de `ChatEngineService` usa un `sender` que guarda la respuesta en el historial. La arquitectura ya resuelve este caso. Propuesta original: **Modo de respuesta automático por dispositivo** (`AUTO`): con gafas conectadas, texto en HUD + voz; con solo audífonos, solo voz (hoy se envía texto a un HUD inexistente); sin dispositivos, voz por el altavoz. Se basa en `BluetoothDeviceManager.activeDevice`. | Alto |
| D2 | **Implementada** (`ai/HudSummary`, `AiResponseDelivery.condenseVisualWhenSpoken`, `Prefs.hudCondensedAnswers`, por defecto activo, solo en la instancia de las gafas). **Texto distinto para HUD y para voz**: el HUD recibe un resumen corto (≤ 2 líneas o ~120 caracteres) y la voz la respuesta completa. Hoy ambos reciben el mismo texto largo. | Alto |
| D3 | **Paginación del HUD** con swipe adelante/atrás mientras hay una respuesta abierta (reutilizando `GlassesEventHandler`). | Medio |
| D4 | **Barge-in en audífonos**: tocar el audífono durante el TTS corta la reproducción y abre el micrófono (`HeadphoneGestureManager` y `AiResponseDelivery.cancel()`). | Medio |
| D5 | Confirmación de acciones sensibles (ya hecha, `SensitiveActionGate`): mostrar el destinatario y el mensaje en el HUD y aceptar "confirmar" con doble toque. | Medio |
| D6 | Cache del modo de escucha activa (`StateFlow`) para eliminar R3. | Bajo |

---

## 2. Fase 1: Backup/Restore v2 (implementación)

**Formato v2** (`format_version: 2`), compatible con la lectura de v1:
```
manifest.json      format_version, app_version (PackageManager), db_version, timestamp, counts, files{ruta: sha256}
database.db        myvu_client.db tras wal_checkpoint(TRUNCATE)
myvu_chat.db       Room tras wal_checkpoint(TRUNCATE)
preferences.json   {"key": {"t": "string|int|long|float|bool|set", "v": ...}}, sin secretos
media/<carpeta>/<archivo>   la carpeta lógica conserva el origen (voice_recordings_ext, attachments, audio, ...)
```

**Creación:**
1. Escribe en streaming directo al zip (sin carpeta temporal) y calcula el SHA-256 de cada entrada mientras escribe.
2. Escribe primero en `*.partial` y luego lo renombra a `myvu-backup-yyyyMMdd-HHmmss.zip`. Rota y conserva los 5 más recientes.
3. Excluye de `preferences.json` las claves con `token`, `secret`, `api_key`, `password` o `credential` (B1).
4. Copia a Downloads con el mismo nombre de archivo.

**Restauración:**
1. Descomprime con protección Zip Slip correcta (separador) y límites: 10 000 entradas y 2 GiB (B5).
2. Valida el manifiesto, los checksums (v2) y que `db_version` ≤ la versión actual (B4). **Todo se valida antes de tocar los datos vivos.**
3. `LocalDatabase` se cierra y su archivo se reemplaza (`SQLiteOpenHelper` vuelve a abrir solo). **`AppDatabase` (Room) no se cierra**: sus tablas se copian con `ATTACH` + `DELETE`/`INSERT` en **una transacción** sobre la conexión abierta. Varios singletons guardan sus DAOs y Room no puede reabrirse después de `close()`; el primer intento, que cerraba Room, rompió `TotalDisconnectHelperTest` justo por eso (B2). Las columnas se emparejan por nombre.
4. Guarda una copia de rollback de las DB actuales y de las preferencias. Si algo falla, lo restaura (B3).
5. Reemplaza las DB con copia y `rename` dentro de la misma carpeta. Fusiona las preferencias conservando su tipo. Devuelve los medios a su carpeta de origen (B6). Los respaldos v1 siguen el mapeo por extensión.
6. `RestoreResult.requiresRestart = true`. La UI ofrece "Reiniciar ahora" (B10) y corrige el mensaje sobre las claves de IA (B9).

**Refactor:** `BackupManager` se divide en `BackupArchive` (zip seguro + SHA-256), `PreferencesCodec` (export/import con tipos) y `BackupManager` (orquestación).

**Tests (`BackupManagerTest`, Robolectric):**
- ida y vuelta (preferencias con tipos y medios);
- exclusión de secretos;
- zip alterado rechazado sin modificar los datos;
- Zip Slip rechazado;
- `db_version` futura rechazada;
- respaldo v1 heredado restaurable;
- rotación.

## 3. Fase 2: Recursos (implementación)
1. **R1:** `deliver()` procesa las skills en el `worker` y solo vuelve al hilo principal para entregar.
2. **R2:** `MeetingAiProcessor` usa `applicationContext` y expone `release()` (`executor.shutdown()`). Las dos Activities lo llaman en `onDestroy()`.

## 4. Fase 3: Dispositivos (propuestas D1–D6)
Pendientes de aprobación. D1 y D2 son las de mayor impacto.

## 5. Verificación
`./gradlew testDebugUnitTest assembleDebug`. Prueba manual: crear un respaldo, verificar que `preferences.json` no contiene `gdrive_*token`, restaurar en el mismo teléfono y comprobar el reinicio, las notas, el chat, las grabaciones y los adjuntos.
