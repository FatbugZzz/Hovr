# Hovr

> Floating image & GIF viewer for Android — sits on top of every other app.

[![Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/fatbug)
## Contact

For support or inquiries:
📧 ghostdiez10@gmail.com

---

## 🇬🇧 English

### What is Hovr?

Hovr is an Android overlay app that lets you float images and animated GIFs on top of any other app on your screen. Drag, resize, rotate, and control playback speed — all while keeping your other apps fully usable underneath.

Inspired by reference-viewer tools like Anima Engine on PC.

---

### Project structure

```
Hovr/
├── app/
│   └── src/main/
│       ├── java/com/hovr/
│       │   ├── MainActivity.kt          — Main screen, favorites & history tabs
│       │   ├── OverlayService.kt        — Core floating window service (multi-instance)
│       │   ├── LocaleManager.kt         — Language switching (ES / EN)
│       │   ├── NightModeManager.kt      — Auto night mode scheduling
│       │   ├── HistoryManager.kt        — Recent items (last 10), JSON-backed
│       │   ├── FavoritesManager.kt      — Persistent favorites (SharedPreferences)
│       │   ├── HovrWidgetProvider.kt    — Home screen widget
│       │   ├── UrlLoaderDialog.kt       — URL input dialog with clipboard detection
│       │   ├── FavoritesAdapter.kt      — Grid adapter for favorites
│       │   └── HistoryAdapter.kt        — List adapter for history
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml    — Main screen layout
│           │   ├── overlay_window.xml   — Floating overlay layout
│           │   ├── item_favorite.xml    — Favorite grid card
│           │   └── item_history.xml     — History list row
│           ├── values/                  — Default strings (Spanish fallback)
│           ├── values-es/               — Spanish strings
│           ├── values-en/               — English strings
│           └── drawable/               — Vector icons, backgrounds, pill buttons
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

### Build instructions

**Requirements**
- Android Studio Hedgehog 2023.1.1 or newer
- Java 21 (bundled with recent Android Studio versions)
- Android SDK 35
- A device or emulator running Android 8.0+ (API 26+)

**Steps**

1. Unzip the project and open Android Studio
2. **File → Open** → select the `Hovr/` root folder
3. Wait for Gradle sync to complete — dependencies download automatically
4. If you see a `local.properties` error, go to **File → Project Structure → SDK Location** and verify the Android SDK path
5. Connect a device via USB with **USB Debugging** enabled, or start an emulator (API 26+)
6. Press **▶ Run** (`Shift + F10`)

---

### First-time setup on device

The app requires one special permission that must be granted manually:

1. On first launch, tap **gallery** or **url**
2. The app opens the system **"Display over other apps"** settings screen automatically
3. Find **Hovr** in the list and enable the toggle
4. Return to the app and try again

---

### Features

| Feature | Details |
|---|---|
| Floating overlay | Up to **3 simultaneous** overlays, each independently controllable |
| Open from gallery | Supports JPG, PNG, WebP, GIF |
| Open from URL | Paste any direct image/GIF link — auto-detects from clipboard |
| Drag to move | Single finger on image or drag handle |
| Pinch to resize | Two-finger pinch — range 120dp to 420dp wide |
| Rotate | Tap ↻ to rotate 90° clockwise — bitmap-level for images, canvas-level for GIFs |
| GIF playback speed | 0.25× · 0.5× · 1× · 2× · 4× |
| Opacity control | 20% to 100% slider |
| Position lock | 🔒 locks drag and pinch |
| Bubble mode | Collapses to a small circle — long press to expand |
| Hide toolbar | Long press on image (normal mode) to hide/show the top bar |
| Favorites | ☆ saves any overlay to persistent favorites |
| History | Last 10 opened items, with thumbnails and relative timestamps |
| Night mode | Auto-reduces opacity on a schedule (default 22:00–08:00) |
| Home screen widget | Shows last favorite — tap to launch overlay directly |
| Language switching | Toggle between Spanish and English via the EN/ES button in the header |
| Soft dark warm theme | `#1A1612` base, amber `#F0A050` accent, terracotta `#C8855A` secondary |

---

### Overlay button reference

| Button | Action |
|---|---|
| ⠿ (grip) | Drag the window anywhere |
| 🌙 / ☀ | Toggle auto night mode |
| 🔓 / 🔒 | Lock / unlock position and resize |
| ↻ | Rotate 90° clockwise |
| ⊡ | Enter bubble mode (long press bubble to expand) |
| ⌄ / ⌃ | Expand / collapse controls panel |
| ☆ / ★ | Save / remove from favorites |
| ✕ | Close this overlay only |

**Gestures on the image**

| Gesture | Action |
|---|---|
| 1 finger drag | Move the overlay window |
| 2 finger pinch | Resize the overlay |
| Long press (0.5s) | Show / hide toolbar |

---

### Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw over other apps |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Access gallery (Android 13+ / below) |
| `INTERNET` | Load images from remote URLs |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep overlay alive in background |
| `POST_NOTIFICATIONS` | Persistent notification with close action (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Restore widget state after reboot |

---

### Language switching

The app supports Spanish (default) and English. Tap the **EN / ES** button in the top-right of the header to switch. The choice persists across sessions.

Translations live in:
- `res/values/strings.xml` — default fallback (Spanish)
- `res/values-es/strings.xml` — Spanish
- `res/values-en/strings.xml` — English

To add a new language, create `res/values-{code}/strings.xml` (e.g. `values-fr/`) with translated strings, add the new locale code to `LocaleManager`, and add a button state for it.

---

### Technical notes

- GIF playback uses `android.graphics.Movie` (native Android API, no external libraries)
- GIF rotation applies a two-step pipeline per frame: draw frame to temp bitmap → rotate that bitmap with `Matrix` → display. This avoids canvas transform artifacts.
- Images use `BitmapFactory` + `ContentResolver.openInputStream()` to avoid URI permission issues when the Activity moves to background. The original bitmap is saved on load so each rotation is applied cleanly from the source, preventing quality degradation.
- Multi-overlay architecture: each `OverlayInstance` is a self-contained inner class inside `OverlayService` with its own state, binding, Handler, and lifecycle. The service manages a `MutableList<OverlayInstance>` of up to 3.
- Long press uses a dedicated `Handler` (`longPressHandler`) per instance, separate from the GIF playback `Handler` (`mainHandler`), to avoid cancelling animation loops when the long press is cancelled.
- Language switching wraps the Activity context in `attachBaseContext()` via `LocaleManager.wrap()` using `createConfigurationContext()`. Switching calls `recreate()` which rebuilds all views with the new locale — no restart required.

---

---

## 🇪🇸 Español

### ¿Qué es Hovr?

Hovr es una app de overlay para Android que te permite flotar imágenes y GIFs animados por encima de cualquier otra app en tu pantalla. Arrastra, redimensiona, rota y controla la velocidad de reproducción — mientras mantienes las otras apps completamente usables por debajo.

Inspirado en herramientas de visor de referencia como Anima Engine en PC.

---

### Estructura del proyecto

```
Hovr/
├── app/
│   └── src/main/
│       ├── java/com/hovr/
│       │   ├── MainActivity.kt          — Pantalla principal, pestañas de favoritos e historial
│       │   ├── OverlayService.kt        — Servicio de ventana flotante (multi-instancia)
│       │   ├── LocaleManager.kt         — Cambio de idioma (ES / EN)
│       │   ├── NightModeManager.kt      — Modo nocturno automático con horario
│       │   ├── HistoryManager.kt        — Últimos 10 elementos abiertos, almacenado en JSON
│       │   ├── FavoritesManager.kt      — Favoritos persistentes (SharedPreferences)
│       │   ├── HovrWidgetProvider.kt    — Widget de pantalla de inicio
│       │   ├── UrlLoaderDialog.kt       — Diálogo de URL con detección del portapapeles
│       │   ├── FavoritesAdapter.kt      — Adapter de grilla para favoritos
│       │   └── HistoryAdapter.kt        — Adapter de lista para historial
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml    — Layout de pantalla principal
│           │   ├── overlay_window.xml   — Layout del overlay flotante
│           │   ├── item_favorite.xml    — Tarjeta de favorito
│           │   └── item_history.xml     — Fila de historial
│           ├── values/                  — Strings por defecto (fallback español)
│           ├── values-es/               — Strings en español
│           ├── values-en/               — Strings en inglés
│           └── drawable/               — Iconos vectoriales, fondos, botones pill
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

### Cómo compilar

**Requisitos**
- Android Studio Hedgehog 2023.1.1 o superior
- Java 21 (incluido en versiones recientes de Android Studio)
- Android SDK 35
- Dispositivo o emulador con Android 8.0+ (API 26+)

**Pasos**

1. Descomprime el proyecto y abre Android Studio
2. **File → Open** → selecciona la carpeta raíz `Hovr/`
3. Espera que Gradle sincronice — las dependencias se descargan automáticamente
4. Si aparece un error de `local.properties`, ve a **File → Project Structure → SDK Location** y verifica la ruta del Android SDK
5. Conecta un dispositivo por USB con **Depuración USB** activada, o inicia un emulador (API 26+)
6. Presiona **▶ Run** (`Shift + F10`)

---

### Configuración en el dispositivo (primer uso)

La app requiere un permiso especial que debe concederse manualmente:

1. Al primer uso, toca **galería** o **url**
2. La app abre automáticamente la pantalla de ajustes **"Mostrar sobre otras apps"**
3. Busca **Hovr** en la lista y activa el toggle
4. Vuelve a la app e intenta de nuevo

---

### Funciones

| Función | Detalles |
|---|---|
| Overlay flotante | Hasta **3 simultáneos**, cada uno controlable de forma independiente |
| Abrir desde galería | Soporta JPG, PNG, WebP, GIF |
| Abrir desde URL | Pega cualquier enlace directo — detecta automáticamente del portapapeles |
| Arrastrar | Un dedo sobre la imagen o el handle de arrastre |
| Pellizcar para redimensionar | Pinch de dos dedos — rango de 120dp a 420dp de ancho |
| Rotar | Toca ↻ para rotar 90° en sentido horario |
| Velocidad de GIF | 0.25× · 0.5× · 1× · 2× · 4× |
| Control de opacidad | Slider del 20% al 100% |
| Bloqueo de posición | 🔒 bloquea arrastre y redimensionado |
| Modo burbuja | Se colapsa en un círculo pequeño — mantén presionado para expandir |
| Ocultar barra | Mantén presionada la imagen (modo normal) para ocultar/mostrar la barra superior |
| Favoritos | ☆ guarda cualquier overlay en favoritos persistentes |
| Historial | Últimos 10 elementos abiertos, con miniaturas y tiempo relativo |
| Modo nocturno | Reduce la opacidad automáticamente según horario (por defecto 22:00–08:00) |
| Widget de inicio | Muestra el último favorito — tócalo para lanzar el overlay directamente |
| Cambio de idioma | Alterna entre español e inglés con el botón EN/ES en el header |
| Tema soft dark cálido | Base `#1A1612`, acento ámbar `#F0A050`, secundario terracota `#C8855A` |

---

### Referencia de botones del overlay

| Botón | Acción |
|---|---|
| ⠿ (grip) | Arrastra la ventana a cualquier posición |
| 🌙 / ☀ | Activa/desactiva el modo nocturno automático |
| 🔓 / 🔒 | Bloquea / desbloquea posición y redimensionado |
| ↻ | Rota 90° en sentido horario |
| ⊡ | Entra en modo burbuja (mantén presionado la burbuja para expandir) |
| ⌄ / ⌃ | Expande / colapsa el panel de controles |
| ☆ / ★ | Guarda / elimina de favoritos |
| ✕ | Cierra solo este overlay |

**Gestos sobre la imagen**

| Gesto | Acción |
|---|---|
| 1 dedo arrastra | Mueve la ventana del overlay |
| 2 dedos pellizcan | Redimensiona el overlay |
| Mantener presionado (0.5s) | Mostrar / ocultar barra superior |

---

### Permisos

| Permiso | Para qué |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Dibujar sobre otras apps |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Acceder a la galería (Android 13+ / anterior) |
| `INTERNET` | Cargar imágenes desde URLs remotas |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Mantener el overlay vivo en segundo plano |
| `POST_NOTIFICATIONS` | Notificación persistente con acción de cerrar (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Restaurar el estado del widget tras reinicio |

---

### Cambio de idioma

La app soporta español (por defecto) e inglés. Toca el botón **EN / ES** en la esquina superior del header para cambiar. La elección persiste entre sesiones.

Las traducciones están en:
- `res/values/strings.xml` — fallback por defecto (español)
- `res/values-es/strings.xml` — español
- `res/values-en/strings.xml` — inglés

Para agregar un nuevo idioma, crea `res/values-{código}/strings.xml` (ej. `values-fr/`) con los strings traducidos, agrega el código del locale a `LocaleManager`, y agrega un estado del botón para él.

---

### Notas técnicas

- La reproducción de GIFs usa `android.graphics.Movie` (API nativa de Android, sin librerías externas)
- La rotación de GIFs usa un pipeline de dos pasos por frame: dibujar el frame en un bitmap temporal → rotar ese bitmap con `Matrix` → mostrar. Esto evita artefactos de transformación de canvas.
- Las imágenes usan `BitmapFactory` + `ContentResolver.openInputStream()` para evitar problemas de permisos de URI cuando la Activity pasa a segundo plano. El bitmap original se guarda al cargar para que cada rotación se aplique desde el original, evitando degradación de calidad.
- Arquitectura multi-overlay: cada `OverlayInstance` es una inner class autocontenida dentro de `OverlayService` con su propio estado, binding, Handler y ciclo de vida. El servicio gestiona una `MutableList<OverlayInstance>` de hasta 3.
- El long press usa un `Handler` dedicado (`longPressHandler`) por instancia, separado del `Handler` de reproducción de GIFs (`mainHandler`), para evitar cancelar los loops de animación.
- El cambio de idioma envuelve el contexto de la Activity en `attachBaseContext()` mediante `LocaleManager.wrap()` usando `createConfigurationContext()`. El cambio llama a `recreate()` que reconstruye todas las vistas con el nuevo locale — sin necesidad de reiniciar la app.
