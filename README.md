# Bitácora del Camionero

App Android para que los choferes de camión lleven su bitácora de viaje desde el celular, incluso sin señal. Proyecto del Taller de Sistemas de Información (UTEM, 2.º semestre 2026).

El chofer crea el viaje con la revisión del camión, registra paradas, descansos, combustible, peajes y novedades durante la ruta, y al llegar cierra el viaje y ve un resumen con la ganancia y el combustible estimado contra el cargado. Todo se guarda en el teléfono; la sincronización con la nube (Supabase) y las pantallas del administrador están en desarrollo.

## Documentos

- Informe del proyecto: `informe 1.2 taller de sistemas de la informacion.docx` (y su PDF). La versión de trabajo del grupo está en Google Docs.
- Diseño acordado: [docs/superpowers/specs/2026-11-10-bitacora-camionero-design.md](docs/superpowers/specs/2026-11-10-bitacora-camionero-design.md)
- Plan de implementación por tareas: [docs/superpowers/plans/2026-11-10-bitacora-camionero.md](docs/superpowers/plans/2026-11-10-bitacora-camionero.md)
- Esquema de la base de datos (PostgreSQL / Supabase): [supabase/schema.sql](supabase/schema.sql)
- Capturas de la app: [docs/capturas](docs/capturas)

## Cómo probar la app

La forma más simple es instalar el APK de la demo, que se comparte aparte (los APK no se suben al repositorio). En el teléfono hay que permitir la instalación de apps de fuentes desconocidas.

Para compilarla:

1. Instalar Android Studio y abrir esta carpeta.
2. Esperar a que Gradle sincronice.
3. Ejecutar en un emulador o teléfono con Android 8.0 o superior.

Desde la terminal también funciona:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`.

Pruebas:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

La segunda necesita un emulador o teléfono conectado.

## Estructura

```
app/src/main/java/com/sistemasdeinformacion/proyecto/
  data/local      Base de datos del teléfono (Room)
  data/repo       Reglas del negocio: viajes, registros, ubicación
  domain          Cálculos y validaciones (horas de manejo, resumen, RUT, patentes)
  feature/...     Pantallas: inicio, nuevo viaje, hoja de ruta, entrega, resumen, perfil
  ui/             Tema visual, componentes y navegación
  sync            Estado de sincronización (la sincronización con Supabase viene después)
supabase/         Script de la base de datos en la nube
docs/             Diseño, plan, capturas y fuente del informe
```

## Tecnologías

Kotlin, Jetpack Compose con Material 3, Navigation 3, Room, WorkManager, DataStore, Supabase (PostgreSQL, autenticación, API REST y almacenamiento) y Google Play Services Location.

## Estado (11 de noviembre)

Listo: flujo completo del chofer sin conexión y 39 pruebas automatizadas.

Pendiente: Supabase, inicio de sesión, sincronización, pantallas del administrador, foto o firma en la entrega y pruebas en teléfonos reales.
