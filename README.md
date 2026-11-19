# Bitácora del Camionero

App Android para que los choferes de camión lleven su bitácora de viaje desde el celular, incluso sin señal. Proyecto del Taller de Sistemas de Información (UTEM, 2.º semestre 2026).

El chofer crea el viaje con la revisión del camión, registra paradas, descansos, combustible, peajes y novedades durante la ruta, y al llegar cierra el viaje y ve un resumen con la ganancia y el combustible estimado contra el cargado. Todo se guarda primero en el teléfono y se envía solo a la nube (Supabase) cuando hay señal. El administrador revisa y aprueba las bitácoras, ve reportes mensuales y mantiene los camiones desde la misma app.

## Documentos

- Informe final: `2026-2-TSI-IFSI-GRPx-INF.pdf` (y su .docx). La versión de trabajo del grupo está en Google Docs.
- Guion de la demostración: [docs/demo.md](docs/demo.md)
- Configuración de Supabase: [docs/supabase-configuracion.md](docs/supabase-configuracion.md)
- Diseño acordado: [docs/superpowers/specs/2026-11-10-bitacora-camionero-design.md](docs/superpowers/specs/2026-11-10-bitacora-camionero-design.md)
- Plan de implementación por tareas: [docs/superpowers/plans/2026-11-10-bitacora-camionero.md](docs/superpowers/plans/2026-11-10-bitacora-camionero.md)
- Esquema de la base de datos (PostgreSQL / Supabase): [supabase/schema.sql](supabase/schema.sql)
- Capturas de la app: [docs/capturas](docs/capturas)

## Cómo probar la app

La forma más simple es instalar el APK del grupo (`BitacoraCamionero-release.apk`), que se comparte por Drive porque los APK no se suben al repositorio. Ese APK ya viene conectado a la base de datos. En el teléfono hay que permitir la instalación de apps de fuentes desconocidas.

## Conectar con Supabase (si compilas tú)

Las llaves del servidor no están en el repositorio. Sin ellas la app compila y abre, pero **no puede crear cuentas, iniciar sesión ni enviar datos**: en la pantalla de inicio de sesión aparece un aviso rojo que lo dice.

1. Pídele a Ignacio las dos líneas de Supabase.
2. Pégalas al final del archivo `local.properties`, en la raíz del proyecto (Android Studio lo crea solo la primera vez que abres el proyecto):

   ```
   supabase.url=https://<referencia>.supabase.co
   supabase.anonKey=<llave anon>
   ```

3. Vuelve a compilar e instala de nuevo.

`local.properties` no se sube a Git. La llave `anon` es pública por diseño: lo que protege los datos son las reglas de la base de datos.

## Compilar

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
  sync            Sincronización con Supabase (WorkManager)
supabase/         Script de la base de datos en la nube
docs/             Diseño, plan, capturas y fuente del informe
```

## Tecnologías

Kotlin, Jetpack Compose con Material 3, Navigation 3, Room, WorkManager, DataStore, Supabase (PostgreSQL, autenticación, API REST y almacenamiento) y Google Play Services Location.

## Estado

Listo: flujo completo del chofer sin señal, inicio de sesión y registro, sincronización con Supabase, foto o firma en la entrega, pantallas del administrador (viajes en vivo, aprobación, reportes y camiones) y 189 pruebas automatizadas.

Pendiente: pruebas en el teléfono del cliente durante un viaje real.
