# Plan: VoIP Calling (WhatsApp, Teams, Google Chat) and Health Services Integration (Steps, Stress, Activity)

## 1. Context & Objectives
The user requested two major capabilities:
1. **VoIP Calling via Voice Commands**:
   - WhatsApp Call to a specific contact ("llama a [contacto] por whatsapp").
   - Microsoft Teams Call ("llama a [contacto] por teams").
   - Google Chat / Google Meet Call ("llama a [contacto] por google chat" or "meet").
2. **Health Services Integration**:
   - Summarize daily steps, stress levels, heart rate, calories, and overall physical activity.
   - Built-in hardware sensor tracking (`Sensor.TYPE_STEP_COUNTER`, `ACTIVITY_RECOGNITION`).
   - Wearable sync via health notification listener (Samsung Health, Google Fit, Mi Fitness, Zepp, Garmin, Huawei).

---

## 2. Technical Architecture & Phased Implementation

### Phase 1: Contact Resolution Enhancements (`ContactHelper.kt`)
- Add `resolveContactEmail(context, query): Pair<String, String>?` to query `ContactsContract.CommonDataKinds.Email`.
- Add `resolveWhatsAppVoipDataId(context, nameOrPhone): Long?` to query `ContactsContract.Data` for `MIMETYPE = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"`.
- Add `resolveGoogleMeetDataId(context, nameOrPhone): Long?` to query `ContactsContract.Data` for `vnd.android.cursor.item/vnd.google.android.apps.tachyon.phone`.

### Phase 2: VoIP Calling Engine (`PhoneActionExecutor.kt` & `AutoSendAccessibilityService.kt`)
- `PhoneActionExecutor`:
  - `makeWhatsAppCall(target: String?)`:
    - Priority 1: If `dataId` exists, dispatch direct VoIP call via `ContactsContract.Data.CONTENT_URI`.
    - Priority 2: Launch WhatsApp chat with `https://api.whatsapp.com/send?phone=...` and trigger `AutoSendAccessibilityService.startAutoCall()`.
    - Handle screen lock with `SendTrampolineActivity`.
  - `makeTeamsCall(target: String?)`:
    - Deep link: `https://teams.microsoft.com/l/call/0/0?users=${Uri.encode(user)}` with package `com.microsoft.teams`.
    - Route via trampoline if locked.
  - `makeGoogleChatCall(target: String?)`:
    - Deep link: `https://meet.google.com/call/${Uri.encode(target)}` or `https://chat.google.com/dm/${Uri.encode(target)}` with package `com.google.android.apps.tachyon` or `com.google.android.apps.dynamite`.
    - Route via trampoline if locked.
- `AutoSendAccessibilityService`:
  - Add `startAutoCall(windowMs: Long)`.
  - When in WhatsApp, find voice call button (`com.whatsapp:id/voice_call`, or description "Llamada de voz" / "Voice call") and click it.

### Phase 3: Health Integration Engine (`HealthService.kt`)
- Create `com.myvu.client.health.HealthService`:
  - Hardware Pedometer listener (`Sensor.TYPE_STEP_COUNTER`, `Sensor.TYPE_STEP_DETECTOR`).
  - Daily steps tracking with start-of-day baseline and SharedPreferences persistence.
  - Stress level monitoring (0-100 score, categorization: Bajo/Relajado, Moderado, Elevado).
  - Active calories burned calculation and estimated distance.
  - Wearable health notification parsing in `MirrorNotificationListener`:
    - Scrapes steps, stress, heart rate from companion apps (Samsung Health, Google Fit, Zepp Life, Mi Fitness, Garmin, Huawei).
  - Summaries:
    - `getStepsSummary()`
    - `getStressSummary()`
    - `getHeartRateSummary()`
    - `getFullHealthSummary()`

### Phase 4: Fast-Paths, Routing & Skills
- `VoiceActionRouter.kt`:
  - VoIP call patterns:
    - WhatsApp: `llama(r)? a (.+) por whatsapp`, `videollamada a (.+) por whatsapp`, etc.
    - Teams: `llama(r)? a (.+) por teams`, `inicia llamada de teams con (.+)`, etc.
    - Google Chat / Meet: `llama(r)? a (.+) por (google chat|meet|google meet)`, etc.
  - Health patterns:
    - Steps: `(cuantos|mis)?\\s*pasos\\s*(llevo|de hoy|hoy)?`, `cuanto he caminado`, `podometro`.
    - Stress: `(nivel de\\s*)?estres`, `como esta mi estres`, `estoy estresado`.
    - Full summary: `resumen de salud`, `resumen de actividad`, `como esta mi salud`.
- Skills:
  - `voip-call/SKILL.md` and `VoipCallHandler.kt`.
  - `health-summary/SKILL.md` and `HealthSummaryHandler.kt`.
  - Register in `SkillRegistry.kt`.
- Permissions:
  - Add `ACTIVITY_RECOGNITION` and `BODY_SENSORS` to `AndroidManifest.xml`.

### Phase 5: Verification & Testing
- Unit tests for:
  - `VoiceActionRouterTest`: WhatsApp call, Teams call, Google Chat call, Steps query, Stress query, Health summary.
  - `HealthServiceTest`: Steps tracking, baseline calculation, stress parsing.
- Build test & APK generation.
- Documentation update in `PROJECT_MEMORY.md` and `README.md`.
