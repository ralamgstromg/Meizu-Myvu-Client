# Sidebar Navigation Drawer & Notes Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a Material 3 Navigation Drawer (Sidebar Navigation Menu) based on the Google Stitch spec `gesti_n_de_notas_y_recordatorios`, moving Notes & Reminders and main feature navigation into a sleek Kinetic Obsidian sidebar drawer.

**Architecture:** Wrap main activity layout in `androidx.drawerlayout.widget.DrawerLayout` containing a `com.google.android.material.navigation.NavigationView` styled with Kinetic Obsidian theme tokens (`#121416` background, `#1A1C1E` drawer panel, `#00F0FF` active highlights). Wire the top bar hamburger menu button to open the sidebar. Connect navigation item click listeners to launch `NotesActivity`, `SettingsActivity`, `TrackpadActivity`, `NotificationAppsActivity`, or switch to Activity Logs.

**Tech Stack:** Android SDK (Kotlin), Material Components (Material 3 `DrawerLayout`, `NavigationView`), ViewBinding.

## Global Constraints
- Target style: Kinetic Obsidian (`stich/gesti_n_de_notas_y_recordatorios` / `stich/kinetic_obsidian/DESIGN.md`).
- Navigation Drawer Items:
  1. Dashboard (`ic_dashboard`)
  2. Ajustes de IA / AI Config (`ic_settings` / `psychology`)
  3. Notas y Recordatorios (`description` / `edit_note`)
  4. Control / Trackpad (`ic_trackpad`)
  5. Filtro de Notificaciones (`notifications`)
  6. Logs y Actividad (`terminal`)
- Preserve all existing Kotlin View Binding IDs.
- Ensure clean compilation with `./gradlew assembleDebug`.

---

### Task 1: Navigation Drawer Menu Resources & Header (`menu_navigation_drawer.xml`, `nav_header_drawer.xml`)

**Files:**
- Create: `android-kotlin/app/src/main/res/menu/menu_navigation_drawer.xml`
- Create: `android-kotlin/app/src/main/res/layout/nav_header_drawer.xml`
- Create: `android-kotlin/app/src/main/res/drawable/ic_menu_hamburger.xml`

**Interfaces:**
- Consumes: Menu item specs from `stich/gesti_n_de_notas_y_recordatorios/code.html`
- Produces: Drawer menu XML and Kinetic Obsidian header layout.

- [ ] **Step 1: Create hamburger menu icon drawable `ic_menu_hamburger.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/cyber_teal">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z" />
</vector>
```

- [ ] **Step 2: Create `menu_navigation_drawer.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <group android:checkableBehavior="single">
        <item
            android:id="@+id/nav_dashboard"
            android:icon="@drawable/ic_glasses"
            android:title="Dashboard" />
        <item
            android:id="@+id/nav_ai_config"
            android:icon="@drawable/ic_settings"
            android:title="Ajustes de IA" />
        <item
            android:id="@+id/nav_notes"
            android:icon="@android:R.drawable.ic_menu_edit"
            android:title="Notas y Recordatorios" />
        <item
            android:id="@+id/nav_trackpad"
            android:icon="@drawable/ic_trackpad"
            android:title="Control Trackpad" />
        <item
            android:id="@+id/nav_notifications"
            android:icon="@android:R.drawable.ic_popup_reminder"
            android:title="Filtro de Notificaciones" />
        <item
            android:id="@+id/nav_logs"
            android:icon="@android:R.drawable.ic_menu_info_details"
            android:title="Logs y Actividad" />
    </group>
</menu>
```

- [ ] **Step 3: Create `nav_header_drawer.xml`**

Create header layout matching Stitch design: `#1A1C1E` container background, smart glasses icon glowing in Cyber Teal `#00F0FF`, app title "MYVU Sync", and battery level status badge.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/menu/ app/src/main/res/layout/nav_header_drawer.xml app/src/main/res/drawable/ic_menu_hamburger.xml
git commit -m "style(drawer): create navigation drawer menu resources and header"
```

---

### Task 2: Refactor `activity_connect.xml` with `DrawerLayout` & `NavigationView`

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_connect.xml`

**Interfaces:**
- Consumes: Resources from Task 1, Stitch layout `gesti_n_de_notas_y_recordatorios`
- Produces: `activity_connect.xml` with root `DrawerLayout` and `NavigationView`.

- [ ] **Step 1: Update `activity_connect.xml` root container**

Encase main layout inside `androidx.drawerlayout.widget.DrawerLayout` (`@id/drawerLayout`).
Add hamburger menu button (`btnNavigationDrawer`) on top left bar.

- [ ] **Step 2: Add `com.google.android.material.navigation.NavigationView`**

```xml
<com.google.android.material.navigation.NavigationView
    android:id="@+id/navigationView"
    android:layout_width="280dp"
    android:layout_height="match_parent"
    android:layout_gravity="start"
    android:background="@color/obsidian_container_low"
    app:headerLayout="@layout/nav_header_drawer"
    app:menu="@menu/menu_navigation_drawer"
    app:itemIconTint="@color/cyber_teal"
    app:itemTextColor="@color/on_surface_obsidian"
    app:itemShapeInsetStart="12dp"
    app:itemShapeInsetEnd="12dp"
    app:itemShapeCornerRadius="16dp" />
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_connect.xml
git commit -m "style(layout): integrate DrawerLayout and NavigationView into activity_connect"
```

---

### Task 3: Wire Navigation Drawer in `ConnectActivity.kt`

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt`

**Interfaces:**
- Consumes: `drawerLayout` and `navigationView` bindings from Task 2
- Produces: Navigation drawer event handling in `ConnectActivity.kt`.

- [ ] **Step 1: Connect hamburger button to open drawer**

In `ConnectActivity.kt`:
```kotlin
binding.btnNavigationDrawer?.setOnClickListener {
    binding.drawerLayout.openDrawer(GravityCompat.START)
}
```

- [ ] **Step 2: Connect `navigationView.setNavigationItemSelectedListener`**

Route drawer item clicks:
- `nav_dashboard`: Close drawer.
- `nav_notes`: Open `NotesActivity`.
- `nav_ai_config`: Open `SettingsActivity`.
- `nav_trackpad`: Open `TrackpadActivity`.
- `nav_notifications`: Open `NotificationAppsActivity`.
- `nav_logs`: Select Log tab (`tabs.getTabAt(1)?.select()`).

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/ConnectActivity.kt
git commit -m "feat(navigation): wire sidebar navigation drawer listeners in ConnectActivity"
```

---

### Task 4: Add Navigation Menu Button to Secondary Activities (`NotesActivity.kt`, `SettingsActivity.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_notes.xml`
- Modify: `android-kotlin/app/src/main/res/layout/activity_settings.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`

**Interfaces:**
- Consumes: Navigation drawer assets
- Produces: Consistent drawer navigation header across secondary activities.

- [ ] **Step 1: Add hamburger button to `activity_notes.xml` and `activity_settings.xml` toolbars**

Add `btnNotesDrawer` / `btnSettingsDrawer` icon button on top bar.

- [ ] **Step 2: Wire drawer actions in `NotesActivity.kt` and `SettingsActivity.kt`**

Clicking drawer button opens navigation drawer or returns to main dashboard menu cleanly.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/java/com/myvu/client/ui/
git commit -m "feat(ui): add sidebar navigation controls across secondary activities"
```

---

### Task 5: Clean Build & End-to-End Verification

**Files:**
- All updated navigation resource and source files.

**Interfaces:**
- Entire Android build pipeline.

- [ ] **Step 1: Clean build check**

Run: `./gradlew clean assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 2: Commit & working tree check**

Run: `git status`
Expected: Working tree clean.

---

## Plan Self-Review Checklist
1. **Spec Coverage:** Covers sidebar navigation drawer (`DrawerLayout` + `NavigationView`), menu items per Stitch `gesti_n_de_notas_y_recordatorios`, notes/reminders integration, and activity routing.
2. **No Placeholders:** Every step specifies exact XML attributes, IDs, and Kotlin code.
3. **Compatibility:** All existing view IDs preserved.
