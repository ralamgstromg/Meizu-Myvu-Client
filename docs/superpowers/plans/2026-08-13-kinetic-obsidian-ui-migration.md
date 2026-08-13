# Kinetic Obsidian UI Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize and upgrade the Android application UI (`android-kotlin`) to strictly conform to the Google Stitch Kinetic Obsidian design specification (`stich/kinetic_obsidian/DESIGN.md`) across all screens and components.

**Architecture:** Refactor base theme tokens (`colors.xml`, `themes.xml`, shapes, drawables) to implement a dark-first Tonal Layering system (base `#121416`, containers `#1a1c1e` / `#1e2022` / `#282a2c`, Cyber Teal `#00f0ff` accent). Update all XML layout files (`activity_connect.xml`, `activity_settings.xml`, `activity_notes.xml`, `activity_trackpad.xml`, `activity_notification_apps.xml`, list item layouts) to adopt 28dp card corner radii, 16dp button/input radii, 8dp chip radii, and monospaced technical typography (`JetBrains Mono`).

**Tech Stack:** Android SDK (Kotlin), Material Components for Android (Material 3), Android XML Layouts & Resource Tokens.

## Global Constraints
- **Colors:** Match `stich/kinetic_obsidian/DESIGN.md` verbatim:
  - Surface base: `#121416`
  - Container low: `#1a1c1e`
  - Container normal: `#1e2022`
  - Container high: `#282a2c`
  - Container highest: `#333537`
  - Primary (Cyber Teal): `#00f0ff` / `#00dbe9`
  - On Primary: `#00363a`
  - On Surface: `#e2e2e5`
  - On Surface Variant: `#b9cacb`
  - Outline: `#849495`
  - Outline Variant / Ghost Border: `#3b494b`
- **Shapes & Radii:**
  - Containers/Cards: `28dp`
  - Medium Controls (Buttons, Inputs): `16dp`
  - Small Controls (Chips, Badges): `8dp`
  - AI Status Orbs: `100% circular`
- **Typography:** Sans-serif for titles/headers, Monospace for system readouts, labels (`label-md`), and status indicators.
- **Compatibility:** Maintain all existing Kotlin View Binding IDs (`btnConnect`, `txtMac`, `rvLog`, `swMirror`, etc.) to guarantee zero functional regressions.

---

### Task 1: Kinetic Obsidian Design Tokens & Base Theme Setup

**Files:**
- Modify: `android-kotlin/app/src/main/res/values/colors.xml`
- Modify: `android-kotlin/app/src/main/res/values/themes.xml`
- Modify: `android-kotlin/app/src/main/res/values-night/themes.xml`
- Create: `android-kotlin/app/src/main/res/drawable/bg_obsidian_card.xml`
- Create: `android-kotlin/app/src/main/res/drawable/bg_cyber_button.xml`

**Interfaces:**
- Consumes: Design system palette from `stich/kinetic_obsidian/DESIGN.md`
- Produces: Color resources and Material 3 theme attributes consumable by all layout XML files.

- [ ] **Step 1: Update `colors.xml` with Kinetic Obsidian tokens**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Kinetic Obsidian Palette -->
    <color name="obsidian_bg">#121416</color>
    <color name="obsidian_surface_dim">#121416</color>
    <color name="obsidian_surface_bright">#37393B</color>
    <color name="obsidian_container_lowest">#0C0E10</color>
    <color name="obsidian_container_low">#1A1C1E</color>
    <color name="obsidian_container">#1E2022</color>
    <color name="obsidian_container_high">#282A2C</color>
    <color name="obsidian_container_highest">#333537</color>

    <color name="cyber_teal">#00F0FF</color>
    <color name="cyber_teal_dim">#00DBE9</color>
    <color name="cyber_teal_light">#DBFCFF</color>
    <color name="on_cyber_teal">#00363A</color>
    <color name="on_cyber_teal_container">#006970</color>

    <color name="on_surface_obsidian">#E2E2E5</color>
    <color name="on_surface_variant_obsidian">#B9CACB</color>
    <color name="outline_obsidian">#849495</color>
    <color name="outline_variant_obsidian">#3B494B</color>

    <!-- Legacy alias compatibility mapping -->
    <color name="brand">#00F0FF</color>
    <color name="brand_dark">#00DBE9</color>
    <color name="brand_light">#DBFCFF</color>
    <color name="brand_container">#1E2022</color>
    <color name="on_brand_container">#E2E2E5</color>

    <color name="bg_dark">#121416</color>
    <color name="surface_dark">#1A1C1E</color>
    <color name="surface_dark_high">#282A2C</color>
    <color name="on_surface_dark">#E2E2E5</color>
    <color name="on_surface_variant_dark">#B9CACB</color>
    <color name="outline_dark">#3B494B</color>

    <color name="state_idle">#849495</color>
    <color name="state_connecting">#00F0FF</color>
    <color name="state_ready">#2ECC71</color>
    <color name="state_failed">#FFB4AB</color>
</resources>
```

- [ ] **Step 2: Create drawable background tokens for 28dp cards and 16dp buttons**

Create `bg_obsidian_card.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/obsidian_container_low" />
    <corners android:radius="28dp" />
    <stroke android:width="1dp" android:color="@color/outline_variant_obsidian" />
</shape>
```

Create `bg_cyber_button.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/cyber_teal" />
    <corners android:radius="16dp" />
</shape>
```

- [ ] **Step 3: Update `themes.xml` to apply Kinetic Obsidian styling**

Update `themes.xml` to enforce dark surface background (`#121416`), Cyber Teal primary, 28dp card corner styling, and Material 3 dark attributes.

- [ ] **Step 4: Verify build with token updates**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/res/drawable/
git commit -m "style: add Kinetic Obsidian color tokens, shapes, and base theme"
```

---

### Task 2: Modernize Main Dashboard & Connection Screen (`activity_connect.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_connect.xml`
- Modify: `android-kotlin/app/src/main/res/drawable/status_dot.xml`
- Modify: `android-kotlin/app/src/main/res/drawable/scan_ring.xml`

**Interfaces:**
- Consumes: Tokens from Task 1, design layouts from `stich/dashboard_principal_actualizado` and `stich/sincronizando_dispositivo`
- Produces: Kinetic Obsidian UI for `ConnectActivity` preserving all element IDs.

- [ ] **Step 1: Update status dot & scan ring drawables**

Update `status_dot.xml` to circular 14dp orb with Cyber Teal inner tint. Update `scan_ring.xml` to 100% circular ring with `#3B494B` border stroke and subtle `#00F0FF` glow opacity.

- [ ] **Step 2: Refactor `activity_connect.xml` layout**

Re-architect `activity_connect.xml`:
- Set background to `@color/obsidian_bg` (`#121416`).
- Top Bar: Sleek header with Cyber Teal "MYVU" title, JetBrains Mono "CONNECTED / DISCONNECTED" pill, and `btnSettings` icon button.
- Tabs (`TabLayout`): Cyber Teal tab indicator line, obsidian container background `#1A1C1E`.
- Card Containers (`cardStatus`, Notifications, AI Assistant, Teleprompter, System, Navigation):
  - Card style with `app:cardCornerRadius="28dp"`, `app:cardBackgroundColor="@color/obsidian_container_low"`, `app:strokeColor="@color/outline_variant_obsidian"`, `app:strokeWidth="1dp"`.
  - Inner Buttons (`btnConnect`, `btnAsk`, `btnNotify`, etc.): Rounded `16dp` buttons with `@color/cyber_teal` fill or ghost border outline.
  - Text Fields: Flat filled style using `@color/obsidian_container_high` (`#282A2C`) with Cyber Teal hint & focus styling.
- Pairing Overlay (`pairingOverlay`): 28dp glassmorphic overlay with circular scanning rings and smart glasses icon glowing in Cyber Teal.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_connect.xml app/src/main/res/drawable/
git commit -m "style: refactor activity_connect to Kinetic Obsidian design"
```

---

### Task 3: Modernize Notes & Reminders Screen (`activity_notes.xml`, `item_note.xml`, `item_reminder.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_notes.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_note.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_reminder.xml`

**Interfaces:**
- Consumes: Layout designs from `stich/notas_y_recordatorios_actualizado` and `stich/crear_nota_o_recordatorio`
- Produces: Kinetic Obsidian UI for Notes and Reminders list & items.

- [ ] **Step 1: Refactor `activity_notes.xml`**

- Update background to `#121416`.
- Add dark toolbar with Cyber Teal back arrow and title.
- Modernize tab layout ("Notas" vs "Recordatorios") with Cyber Teal accent.
- Floating Action Button (`fabAdd`): Rounded 16dp or circular pill in Cyber Teal with dark icon.

- [ ] **Step 2: Refactor `item_note.xml` and `item_reminder.xml`**

- Encase items in `MaterialCardView` with `28dp` corner radius, `#1E2022` container background, and `1dp` ghost border `#3B494B`.
- Add chip badges with `8dp` corner radius using JetBrains Mono font (`label-md`) for categories ("AI Note", "HUD Sync", "Alarm").
- Format timestamp and details with `#B9CACB` text color.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_notes.xml app/src/main/res/layout/item_note.xml app/src/main/res/layout/item_reminder.xml
git commit -m "style: update notes and reminders to Kinetic Obsidian design"
```

---

### Task 4: Modernize Settings Screen (`activity_settings.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_settings.xml`

**Interfaces:**
- Consumes: Layout designs from `stich/configuraci_n_de_ia_actualizado`
- Produces: Obsidian styled configuration page.

- [ ] **Step 1: Update `activity_settings.xml`**

- Container background: `#121416`.
- Group settings into 28dp rounded obsidian cards (`#1A1C1E`) for "AI Provider", "API Keys", "Audio & TTS", and "System Prompt".
- Inputs: Outlined text boxes with `16dp` corner radius, `#282A2C` background fill, Cyber Teal active border.
- Switches (`MaterialSwitch`): Cyber Teal track / thumb active tint.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_settings.xml
git commit -m "style: modernize settings activity with Kinetic Obsidian components"
```

---

### Task 5: Modernize Trackpad Screen (`activity_trackpad.xml`, `trackpad_surface.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_trackpad.xml`
- Modify: `android-kotlin/app/src/main/res/drawable/trackpad_surface.xml`

**Interfaces:**
- Consumes: Touch surface specs from `stich/kinetic_obsidian/DESIGN.md`
- Produces: Futuristic touch interface visual layout.

- [ ] **Step 1: Update `trackpad_surface.xml` and `activity_trackpad.xml`**

- Update `trackpad_surface.xml` to use `#1A1C1E` dark surface with 28dp rounded corners and `#3B494B` outline stroke.
- Update `activity_trackpad.xml` toolbar, touch gesture guidance text (JetBrains Mono `label-md`), and status indicators in Cyber Teal.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_trackpad.xml app/src/main/res/drawable/trackpad_surface.xml
git commit -m "style: apply obsidian dark theme to trackpad view"
```

---

### Task 6: Modernize Notification Apps Selection Screen (`activity_notification_apps.xml`, `item_app.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_notification_apps.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_app.xml`

**Interfaces:**
- Consumes: Obsidian card rows pattern
- Produces: Modern app toggle list UI.

- [ ] **Step 1: Update `activity_notification_apps.xml` and `item_app.xml`**

- Encase `item_app.xml` in rounded container `#1E2022` with 16dp corner radius and Cyber Teal toggle switch.
- Update `activity_notification_apps.xml` background to `#121416`, search bar to filled 16dp rounded input `#282A2C`.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_notification_apps.xml app/src/main/res/layout/item_app.xml
git commit -m "style: update notification app picker to Kinetic Obsidian layout"
```

---

### Task 7: Modernize Activity Log & Terminal View UI (`LogAdapter.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/LogAdapter.kt`

**Interfaces:**
- Consumes: Activity log visual specs from `stich/logs_y_actividad_actualizado`
- Produces: Technical terminal style log rows.

- [ ] **Step 1: Update `LogAdapter.kt` styling**

- Set text typeface to monospaced (`Typeface.MONOSPACE` / JetBrains Mono).
- Color-code log levels: Cyber Teal `#00F0FF` for AI/System, Green `#2ECC71` for Connection OK, Amber `#F5A623` for sync, Red `#FFB4AB` for errors.
- Text size: 12sp (`label-md`), clean spacing on dark container `#0C0E10`.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/LogAdapter.kt
git commit -m "style: format activity logs with monospaced JetBrains Mono typography and Kinetic Obsidian status tints"
```

---

### Task 8: Full Project Build & Visual Verification

**Files:**
- All updated Android resources and layouts.

**Interfaces:**
- Entire `android-kotlin` build lifecycle.

- [ ] **Step 1: Clean build check**

Run: `./gradlew clean assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 2: Final git commit & state check**

Run: `git status`
Expected: Working tree clean.

---

## Plan Self-Review Checklist
1. **Spec Coverage:** Covers all Stitch design tokens, screens (`dashboard_principal`, `sincronizando_dispositivo`, `configuraci_n_de_ia`, `notas_y_recordatorios`, `logs_y_actividad`, `trackpad`).
2. **No Placeholders:** Every step contains explicit files, XML attributes, colors, and exact build commands.
3. **Type / ID Consistency:** All Kotlin view binding IDs (`btnConnect`, `txtMac`, `rvLog`, `swMirror`, etc.) preserved.
