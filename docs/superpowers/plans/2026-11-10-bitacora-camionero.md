# Bitácora del Camionero — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android (chofer + administrador) que registra bitácoras de viaje completas sin señal y las sincroniza con Supabase, con la identidad visual "Señalética de autopista", más el informe corregido.

**Architecture:** Kotlin + Jetpack Compose (MVVM). Room es la fuente de verdad del chofer; un `Sincronizador` sube pendientes (upsert por UUID) y baja cambios por `actualizado_en`, ejecutado por WorkManager. Supabase aporta Auth, PostgREST, Storage y Realtime; la seguridad vive en RLS y triggers de PostgreSQL. El administrador lee directo de Supabase.

**Tech Stack:** Kotlin 2.1, Compose BOM + Material 3, Navigation 3, Room 2.7 (KSP), WorkManager, DataStore, supabase-kt 3.x (auth, postgrest, storage, realtime) + Ktor OkHttp, Play Services Location, kotlinx.serialization, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-10-bitacora-camionero-design.md` (leer junto con este plan).

## Global Constraints

- Paquete y applicationId: `com.sistemasdeinformacion.proyecto`. minSdk 26, targetSdk/compileSdk el que exija la versión de Navigation 3 que resuelva (≥ 35).
- Textos de la app en español de Chile, frases cortas, verbo + objeto ("Registrar parada", "Finalizar viaje"); errores dicen qué pasó y cómo seguir; sin mayúsculas sostenidas en etiquetas.
- Todo lo del chofer funciona sin red: las pantallas del chofer leen solo de Room; ninguna espera a la red para mostrar o guardar.
- IDs de viajes, revisiones, eventos, gastos, novedades y adjuntos: UUID generado en el teléfono (`Ids.nuevo()`).
- Montos en pesos chilenos enteros (`Long`), formato `$412.300`. Kilómetros enteros, formato `3.240 km`. Fechas en Room como epoch ms; en Supabase como `timestamptz` ISO-8601.
- Credenciales Supabase solo en `local.properties` (`supabase.url`, `supabase.anonKey`), nunca en Git.
- Paleta: asfalto `#1C1F22`, superficie `#26292D`, verde señal `#0E6B4C`, ámbar `#F2A900`, blanco reflectante `#F3F2EC`, gris berma `#A9AFB6`. Tipografía Barlow (texto) y Barlow Condensed (rutas, km, montos).
- Movimiento: un solo momento orquestado (el camino se dibuja al abrir la hoja de ruta); el resto solo responde a acciones; respetar "quitar animaciones" del sistema.
- Reglas de negocio de la sección 11 del spec: límite 5 h de manejo continuo, aviso 30 min antes, descanso ≥ 30 min reinicia; solo viajes finalizados se aprueban; aprobado = solo lectura; editar un observado lo vuelve a pendiente.
- Skills instaladas a usar donde aplique: `edge-to-edge`, `navigation-3`, `android-permissions-security`, `jetpack-compose-performance`.
- JDK: el de Android Studio (`C:\Program Files\Android\Android Studio\jbr`). Comandos de Gradle con `gradlew.bat` desde la raíz del repo.

## Review Focus

1. **Corte de red a mitad de una sincronización** → nada se duplica ni se pierde; lo no confirmado sigue pendiente y se reintenta. (Task 6: `subidaFallaAMitad_reintentaSinDuplicar`)
2. **GPS denegado o sin fijación (dentro de un galpón)** → el evento se guarda igual, sin coordenadas, sin bloquear al chofer. (Task 11: `registrarParada_sinUbicacion_guardaSinCoordenadas`)
3. **App reabierta sin señal con el token vencido** → entra directo al Inicio con sus datos. (Task 8: `sesionGuardada_sinRed_quedaActiva`)
4. **Números escritos a la chilena** ("18.500" kg, "3,5" km/L, km final menor al inicial) → se interpretan bien o se rechazan con mensaje claro. (Task 3: `parsearEntero_aceptaPuntoDeMiles`, `parsearDecimal_aceptaComa`, `kmCoherentes_rechazaFinalMenor`)
5. **Crear un viaje teniendo otro en curso** → no se crea un segundo; se ofrece abrir el que está en curso. (Task 10: `crearViaje_conViajeEnCurso_noCreaOtro`)

---

## Estructura de archivos

Raíz `app/src/main/java/com/sistemasdeinformacion/proyecto/` (abreviada `…/`). Tests JVM en `app/src/test/java/com/sistemasdeinformacion/proyecto/`, instrumentados en `app/src/androidTest/...`.

| Archivo | Responsabilidad |
|---|---|
| `…/BitacoraApp.kt` | Application; crea `AppContainer`; provee configuración de WorkManager |
| `…/MainActivity.kt` | Splash, edge-to-edge, tema, raíz de navegación |
| `…/di/AppContainer.kt` | Construye DB, cliente Supabase, repositorios (inyección manual) |
| `…/core/Ids.kt`, `Reloj.kt` | UUID; reloj inyectable para tests |
| `…/domain/Validaciones.kt`, `Formatos.kt`, `CalculosViaje.kt` | Lógica pura testeable |
| `…/data/model/Enums.kt` | Enumeraciones del dominio (existe; se amplía) |
| `…/data/local/Entities.kt`, `Daos.kt`, `AppDatabase.kt` | Room |
| `…/data/remote/SupabaseModulo.kt`, `SesionCifrada.kt`, `Dtos.kt`, `Mapeos.kt` | Cliente, sesión cifrada, DTOs y conversión |
| `…/data/prefs/Preferencias.kt` | DataStore: perfil en caché, marca de última bajada, tema |
| `…/sync/Sincronizador.kt`, `FuentesSync.kt`, `SyncWorker.kt`, `ProgramadorSync.kt`, `EstadoSync.kt` | Sincronización |
| `…/data/repo/AuthRepositorio.kt`, `ViajesRepositorio.kt`, `AdminRepositorio.kt`, `UbicacionRepositorio.kt` | Repositorios |
| `…/ui/theme/*` | Color, tipografía, tema claro/oscuro |
| `…/ui/componentes/*` | `LetreroRuta`, `CaminoRuta`, `BotonPrincipal`, `IndicadorSync`, `CampoTexto`, `EstadoVacio`, `HojaInferior` |
| `…/ui/navegacion/Destinos.kt`, `NavegacionRaiz.kt` | Navigation 3 |
| `…/feature/auth/*`, `inicio/*`, `viajes/*`, `ruta/*`, `entrega/*`, `perfil/*`, `admin/*` | Pantallas + ViewModels |
| `supabase/schema.sql` | Esquema, RLS, triggers, storage (existe; se amplía) |
| `docs/supabase-configuracion.md` | Pasos manuales en el panel de Supabase |

---

### Task 1: Entorno y esqueleto que compila

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (borradores existentes)
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `…/BitacoraApp.kt`, `…/MainActivity.kt`, `…/di/AppContainer.kt` (vacío por ahora)
- Delete: nada del borrador; `Entities.kt` y `Enums.kt` se ajustan en Tasks 3–4

**Interfaces:**
- Produces: proyecto que compila; `BitacoraApp.container: AppContainer`.

- [ ] **Step 1: El usuario abre Android Studio** y completa el asistente inicial (descarga SDK, acepta licencias). Verificar que exista `%LOCALAPPDATA%\Android\Sdk\platforms`.
- [ ] **Step 2: Generar el wrapper de Gradle** abriendo el proyecto en Android Studio (File > Open sobre la raíz) o con `gradle wrapper`; crear `local.properties` con `sdk.dir`.
- [ ] **Step 3: Agregar dependencias faltantes** al catálogo: `supabase-storage` (`storage-kt`), `supabase-realtime` (`realtime-kt`), Navigation 3 (`androidx.navigation3:navigation3-runtime`, `navigation3-ui`, `androidx.lifecycle:lifecycle-viewmodel-navigation3`, según skill `navigation-3`), `androidx.compose.ui:ui-text-google-fonts`. Quitar `navigation-compose`. Ajustar versiones hasta que resuelvan.
- [ ] **Step 4: `MainActivity`** con `installSplashScreen()`, `enableEdgeToEdge()` y un texto "Bitácora Camionero" centrado.
- [ ] **Step 5: Compilar**
  Run: `gradlew.bat assembleDebug`
  Expected: `BUILD SUCCESSFUL`, APK en `app/build/outputs/apk/debug/`.
- [ ] **Step 6: Commit** — `chore: esqueleto Android compilable`

---

### Task 2: Esquema Supabase completo y configuración del proyecto

**Files:**
- Modify: `supabase/schema.sql`
- Create: `docs/supabase-configuracion.md`

**Interfaces:**
- Produces: tablas `perfiles, camiones, viajes, revisiones, eventos, gastos, novedades, adjuntos`, vista `resumen_viajes`, bucket privado `adjuntos`, funciones `es_admin()`, `es_mi_viaje(uuid)`.

- [ ] **Step 1: Ampliar el esquema**
  - `tipo_evento` agrega `descanso` y `entrega`; `eventos` agrega `fecha_fin timestamptz` con check `fecha_fin is null or fecha_fin >= fecha_hora`.
  - `viajes` agrega `ruta text` (escudo del letrero, opcional, p. ej. "5 SUR").
  - Tabla `revisiones(id uuid pk, viaje_id uuid unique fk, neumaticos bool, luces bool, frenos bool, niveles bool, documentos bool, comentario text, eliminado, creado_en, actualizado_en)`.
  - Tabla `adjuntos(id uuid pk, viaje_id fk, tipo text check in ('foto','firma'), ruta_remota text, nombre_receptor text, fecha_hora timestamptz, eliminado, creado_en, actualizado_en)`.
  - Ambas con trigger `tocar_actualizado` y las mismas políticas RLS que `eventos`.
  - Reemplazar `proteger_campos` para `viajes` (solo usuarios `authenticated` no admin):
    - si `old.validacion = 'aprobado'` → `raise exception 'VIAJE_APROBADO' using errcode = 'P0001'`;
    - si `old.validacion = 'observado'` y el chofer actualiza → `new.validacion := 'pendiente'`;
    - en otro caso conservar `validacion`, `validado_por`, `comentario_validacion`, `chofer_id`.
  - Para admin: si `new.validacion <> old.validacion` y `new.estado <> 'finalizado'` → excepción `SOLO_FINALIZADOS`; fijar `new.validado_por := auth.uid()`.
  - Vista `resumen_viajes` agrega `litros` (suma de `gastos.litros` de combustible) y `exceso_horas boolean` queda fuera (se calcula en la app).
  - Storage: `insert into storage.buckets (id, name, public) values ('adjuntos','adjuntos',false) on conflict do nothing;` políticas sobre `storage.objects` con `bucket_id = 'adjuntos'`: insertar si `es_mi_viaje(((storage.foldername(name))[1])::uuid)`; leer si eso o `es_admin()`.
  - Publicación Realtime: `alter publication supabase_realtime add table viajes;`
- [ ] **Step 2: Escribir `docs/supabase-configuracion.md`** con los pasos manuales: crear proyecto (región São Paulo), ejecutar `schema.sql`, Auth > desactivar "Confirm email", plantilla "Reset password" con `{{ .Token }}`, copiar URL y anon key a `local.properties`, promover admin con el `update` del final del esquema, cargar 3 camiones de ejemplo.
- [ ] **Step 3: El usuario crea el proyecto y ejecuta el esquema.** Verificar en SQL Editor:
  ```sql
  select count(*) from pg_tables where schemaname='public'
    and tablename in ('perfiles','camiones','viajes','revisiones','eventos','gastos','novedades','adjuntos');
  -- Expected: 8
  select relname from pg_class where relrowsecurity and relname in ('viajes','eventos','adjuntos');
  -- Expected: 3 filas
  ```
- [ ] **Step 4: Commit** — `feat(db): esquema completo con RLS, revisiones, adjuntos y storage`

---

### Task 3: Lógica pura del dominio (TDD)

**Files:**
- Modify: `…/data/model/Enums.kt` (agregar `TipoEvento.DESCANSO`, `TipoEvento.ENTREGA`; nuevo `enum TipoAdjunto(FOTO,"foto"), (FIRMA,"firma")`; `enum AlertaHoras { NORMAL, PROXIMA, EXCEDIDA }`)
- Create: `…/core/Ids.kt`, `…/core/Reloj.kt`, `…/domain/Validaciones.kt`, `…/domain/Formatos.kt`, `…/domain/CalculosViaje.kt`
- Test: `…/domain/ValidacionesTest.kt`, `FormatosTest.kt`, `CalculosViajeTest.kt`

**Interfaces:**
- Produces:
  - `object Ids { fun nuevo(): String }`; `fun interface Reloj { fun ahora(): Long }` con `object RelojSistema : Reloj`.
  - `object Validaciones { fun email(s: String): Boolean; fun patente(s: String): Boolean; fun rut(s: String): Boolean; fun parsearEntero(s: String): Int?; fun parsearDecimal(s: String): Double?; fun kmCoherentes(inicial: Int?, final: Int?): Boolean; fun contrasenaValida(s: String): Boolean }`
  - `object Formatos { fun pesos(monto: Long): String; fun km(km: Int): String; fun duracion(ms: Long): String; fun hora(epochMs: Long): String; fun fechaCorta(epochMs: Long): String }`
  - `data class ResumenViaje(val kmRecorridos: Int?, val duracionMs: Long?, val gastoTotal: Long, val gastoPorTipo: Map<TipoGasto, Long>, val ganancia: Long, val litrosReales: Double, val litrosEstimados: Double?, val pesosEstimados: Long?)`
  - `object CalculosViaje { const val LIMITE_MANEJO_MS = 5h; const val AVISO_PREVIO_MS = 30min; const val DESCANSO_MINIMO_MS = 30min; fun resumen(viaje: ViajeEntity, gastos: List<GastoEntity>, rendimientoKmL: Double?): ResumenViaje; fun manejoContinuoMs(inicioViaje: Long, eventos: List<EventoEntity>, ahora: Long): Long; fun alerta(manejoContinuoMs: Long): AlertaHoras }`

- [ ] **Step 1: Tests de validaciones**
  ```kotlin
  @Test fun parsearEntero_aceptaPuntoDeMiles() = assertEquals(18500, Validaciones.parsearEntero("18.500"))
  @Test fun parsearEntero_rechazaTexto() = assertNull(Validaciones.parsearEntero("abc"))
  @Test fun parsearDecimal_aceptaComa() = assertEquals(3.5, Validaciones.parsearDecimal("3,5")!!, 0.0001)
  @Test fun kmCoherentes_rechazaFinalMenor() = assertFalse(Validaciones.kmCoherentes(1000, 900))
  @Test fun kmCoherentes_aceptaNulos() = assertTrue(Validaciones.kmCoherentes(1000, null))
  @Test fun patente_formatosChilenos() { assertTrue(Validaciones.patente("KFZT21")); assertTrue(Validaciones.patente("AB1234")); assertFalse(Validaciones.patente("K1")) }
  @Test fun rut_digitoVerificador() { assertTrue(Validaciones.rut("11.111.111-1")); assertTrue(Validaciones.rut("12345678-5")); assertFalse(Validaciones.rut("12345678-9")) }
  @Test fun contrasena_minimo8ConLetraYNumero() { assertTrue(Validaciones.contrasenaValida("camion2026")); assertFalse(Validaciones.contrasenaValida("corta1")) }
  ```
- [ ] **Step 2: Tests de formatos**
  ```kotlin
  @Test fun pesos() = assertEquals("$412.300", Formatos.pesos(412300))
  @Test fun km() = assertEquals("3.240 km", Formatos.km(3240))
  @Test fun duracion() = assertEquals("5 h 20 min", Formatos.duracion(5*3_600_000L + 20*60_000L))
  @Test fun duracionMenorAUnaHora() = assertEquals("45 min", Formatos.duracion(45*60_000L))
  ```
- [ ] **Step 3: Tests de cálculos**
  ```kotlin
  @Test fun resumen_calculaGananciaYLitros() {
    // viaje precio 900.000, km 1000→1500, gastos: combustible 210.000 (180 L), peaje 4.100; rendimiento 3.0
    val r = CalculosViaje.resumen(viaje, gastos, 3.0)
    assertEquals(500, r.kmRecorridos); assertEquals(214_100L, r.gastoTotal); assertEquals(685_900L, r.ganancia)
    assertEquals(180.0, r.litrosReales, 0.01); assertEquals(166.67, r.litrosEstimados!!, 0.01)
    assertEquals(194_444L, r.pesosEstimados) // 166,67 L × (210.000/180) redondeado
  }
  @Test fun resumen_ignoraGastosEliminados()
  @Test fun manejoContinuo_sinDescansos_cuentaDesdeInicio()        // inicio 06:00, ahora 11:20 → 5h20
  @Test fun manejoContinuo_descansoLargoReinicia()                  // descanso 09:00–09:40 → cuenta desde 09:40
  @Test fun manejoContinuo_paradaCortaNoReinicia()                  // descanso 09:00–09:10 → cuenta desde 06:00
  @Test fun manejoContinuo_descansoEnCurso_esCero()                 // descanso sin fechaFin → 0
  @Test fun alerta_umbrales() { assertEquals(NORMAL, alerta(4h29m)); assertEquals(PROXIMA, alerta(4h30m)); assertEquals(EXCEDIDA, alerta(5h)) }
  ```
- [ ] **Step 4: Correr y ver fallar** — `gradlew.bat testDebugUnitTest` → FAIL (símbolos no definidos).
- [ ] **Step 5: Implementar** las firmas de Interfaces. `pesos`/`km` con separador de miles `.` (Locale `es-CL`). `rut` con módulo 11 (dígito `K` válido). `patente`: `^[A-Z]{4}\d{2}$|^[A-Z]{2}\d{4}$` tras quitar guiones/espacios y pasar a mayúsculas. `manejoContinuoMs`: el reloj parte en `inicioViaje` y se mueve al `fechaFin` de cada `DESCANSO` no eliminado cuya duración ≥ `DESCANSO_MINIMO_MS`; si hay un `DESCANSO` sin `fechaFin`, devuelve 0.
- [ ] **Step 6: Correr y ver pasar** — `gradlew.bat testDebugUnitTest` → PASS.
- [ ] **Step 7: Commit** — `feat(domain): validaciones, formatos y cálculos del viaje`

---

### Task 4: Base local Room

**Files:**
- Modify: `…/data/local/Entities.kt`
- Create: `…/data/local/Daos.kt`, `…/data/local/AppDatabase.kt`
- Test: `app/src/androidTest/.../data/local/DaosTest.kt`

**Interfaces:**
- Consumes: Enums (Task 3).
- Produces:
  - Entidades existentes + `ViajeEntity.ruta: String?` + `EventoEntity.fechaFin: Long?` + `RevisionEntity(id, viajeId, neumaticos, luces, frenos, niveles, documentos: Boolean, comentario: String?, eliminado, modificadoLocal, syncPendiente)` + `AdjuntoEntity(id, viajeId, tipo: TipoAdjunto, rutaLocal: String, rutaRemota: String?, nombreReceptor: String?, fechaHora: Long, eliminado, modificadoLocal, syncPendiente)`. Toda entidad sincronizable implementa `interface Sincronizable { val id: String; val modificadoLocal: Long; val syncPendiente: Boolean }`.
  - `ViajeDao`: `fun observarDelChofer(choferId: String): Flow<List<ViajeEntity>>`, `fun observarEnCurso(choferId: String): Flow<ViajeEntity?>`, `fun observar(id: String): Flow<ViajeEntity?>`, `suspend fun obtener(id: String): ViajeEntity?`, `@Upsert suspend fun guardar(v: ViajeEntity)`.
  - `RegistroDao`: `observarEventos/Gastos/Novedades/Adjuntos(viajeId): Flow<List<…>>` (excluye `eliminado`), `observarRevision(viajeId): Flow<RevisionEntity?>`, `@Upsert guardar(…)` por tipo, `fun observarEventosDelChofer(choferId): Flow<List<EventoEntity>>`, `fun observarGastosDelMes(choferId, desde: Long, hasta: Long): Flow<List<GastoEntity>>`.
  - `CamionDao`: `observarTodos(): Flow<List<CamionEntity>>`, `@Transaction suspend fun reemplazar(camiones: List<CamionEntity>)`.
  - `SyncDao`: por cada tabla `suspend fun <tabla>Pendientes(): List<…>`, `suspend fun marcar<Tabla>Enviado(id: String, modificadoLocal: Long)` (`UPDATE … SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal`), `fun contarPendientes(): Flow<Int>` (suma de las 7 tablas), `suspend fun <tabla>EsPendiente(id): Boolean`.
  - `AppDatabase.crear(context)`; `suspend fun AppDatabase.borrarTodo()`.

- [ ] **Step 1: Tests instrumentados** (DB en memoria)
  ```kotlin
  @Test fun upsert_noBorraHijos()  // guardar viaje, evento; volver a guardar el viaje → evento sigue existiendo
  @Test fun marcarEnviado_soloSiNoCambio() // modificadoLocal 100 → marcar con 99 → sigue pendiente; con 100 → no pendiente
  @Test fun contarPendientes_sumaTablas()  // 1 viaje + 2 gastos pendientes → 3
  @Test fun observarEnCurso_devuelveSoloEnCurso()
  ```
- [ ] **Step 2: Ejecutar en emulador y ver fallar** — `gradlew.bat connectedDebugAndroidTest` → FAIL.
- [ ] **Step 3: Implementar entidades, DAOs y base** (Room `@Upsert`, nunca `REPLACE`, para no disparar el `CASCADE`).
- [ ] **Step 4: Ver pasar** — `gradlew.bat connectedDebugAndroidTest` → PASS.
- [ ] **Step 5: Commit** — `feat(local): base Room con cola de pendientes`

---

### Task 5: Capa remota (Supabase, sesión cifrada, DTOs)

**Files:**
- Create: `…/data/remote/SupabaseModulo.kt`, `SesionCifrada.kt`, `Dtos.kt`, `Mapeos.kt`, `…/data/prefs/Preferencias.kt`
- Test: `…/data/remote/MapeosTest.kt`

**Interfaces:**
- Produces:
  - `fun crearSupabase(sesion: SessionManager): SupabaseClient` (instala Auth con `sessionManager = sesion`, Postgrest, Storage, Realtime; URL y llave desde `BuildConfig`).
  - `class SesionCifrada(context: Context) : SessionManager` — `UserSession` serializado a JSON, cifrado AES-GCM con llave del Android Keystore (alias `bitacora_sesion`), guardado en DataStore.
  - DTOs `@Serializable` con `@SerialName` snake_case: `PerfilDto, CamionDto, ViajeDto, RevisionDto, EventoDto, GastoDto, NovedadDto, AdjuntoDto`, y `ResumenViajeDto` (fila de la vista `resumen_viajes`: id, chofer_id, chofer, patente, cliente, origen, destino, fecha_salida, fecha_llegada, estado, validacion, precio, km_recorridos, gasto_combustible, gasto_peajes, gasto_total, litros, margen). Los DTO de subida **no** incluyen `creado_en`, `actualizado_en`, `validacion`, `validado_por`, `comentario_validacion` (`ViajeSubidaDto`); los de bajada sí (`ViajeDto`).
  - Mapeos: `fun ViajeEntity.aSubida(): ViajeSubidaDto`, `fun ViajeDto.aEntidad(): ViajeEntity` (con `syncPendiente = false`, `modificadoLocal = 0`), análogos por tabla; `fun Long.aIso(): String`, `fun String.desdeIso(): Long` (vía `OffsetDateTime.parse`).
  - `class Preferencias(context)`: `val perfil: Flow<Perfil?>`, `suspend fun guardarPerfil(p: Perfil?)`, `suspend fun marcaBajada(): String?`, `suspend fun guardarMarcaBajada(iso: String)`, `val temaClaro: Flow<Boolean>`, `suspend fun guardarTemaClaro(b: Boolean)`, `suspend fun limpiar()`. `data class Perfil(val id: String, val nombre: String, val email: String, val rol: Rol)`.

- [ ] **Step 1: Tests de mapeo**
  ```kotlin
  @Test fun iso_conOffsetYMicrosegundos() = assertEquals(1794316800123L, "2026-11-10T12:00:00.123456+00:00".desdeIso())
  @Test fun viaje_idaYVuelta() { val e = viajeEjemplo(); assertEquals(e.copy(syncPendiente=false, modificadoLocal=0), e.aSubida().comoDtoBajada().aEntidad()) }
  @Test fun subida_noIncluyeValidacion() { val json = Json.encodeToString(viajeEjemplo().aSubida()); assertFalse("validacion" in json) }
  ```
- [ ] **Step 2: Ver fallar** — `gradlew.bat testDebugUnitTest --tests "*MapeosTest"` → FAIL.
- [ ] **Step 3: Implementar** según Interfaces.
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Commit** — `feat(remote): cliente Supabase, sesión cifrada y DTOs`

---

### Task 6: Sincronización

**Files:**
- Create: `…/sync/FuentesSync.kt`, `Sincronizador.kt`, `SyncWorker.kt`, `ProgramadorSync.kt`, `EstadoSync.kt`
- Test: `…/sync/SincronizadorTest.kt` (con fakes en memoria)

**Interfaces:**
- Consumes: DAOs (Task 4), DTOs/mapeos y `Preferencias` (Task 5).
- Produces:
  - `data class LotePendiente(val viajes: List<ViajeEntity>, val revisiones: List<RevisionEntity>, val eventos: List<EventoEntity>, val gastos: List<GastoEntity>, val novedades: List<NovedadEntity>, val adjuntos: List<AdjuntoEntity>) { val total: Int }`
  - `data class CambiosRemotos(val viajes: List<ViajeDto>, …, val marca: String?)`
  - `interface OrigenLocal { suspend fun pendientes(): LotePendiente; suspend fun marcarEnviado(item: Sincronizable); suspend fun aplicarRemotos(c: CambiosRemotos); suspend fun descartarViaje(viajeId: String) }` — `aplicarRemotos` omite filas cuyo equivalente local está pendiente.
  - `interface DestinoRemoto { suspend fun subirViaje(v: ViajeEntity): ResultadoFila; suspend fun subirLote(tabla: String, items: List<Sincronizable>); suspend fun subirArchivo(a: AdjuntoEntity): String; suspend fun bajarDesde(marca: String?, choferId: String): CambiosRemotos }` con `enum ResultadoFila { OK, RECHAZADO_APROBADO }`.
  - `class Sincronizador(local: OrigenLocal, remoto: DestinoRemoto, marca: suspend () -> String?, guardarMarca: suspend (String) -> Unit, choferId: suspend () -> String?) { suspend fun sincronizar(): ResultadoSync }`
  - `sealed interface ResultadoSync { data class Ok(val enviados: Int, val recibidos: Int, val rechazados: List<String>) : ResultadoSync; data object SinConexion : ResultadoSync; data class Falla(val mensaje: String) : ResultadoSync }`
  - `class ProgramadorSync(context) { fun alGuardar(); fun periodico(); val estado: Flow<EstadoSync> }` — `alGuardar` encola trabajo único (`KEEP`/`APPEND_OR_REPLACE`) con restricción de red; `periodico` cada 15 min.
  - `data class EstadoSync(val pendientes: Int, val enviando: Boolean, val conectado: Boolean, val ultimoError: String?, val viajesRestaurados: List<String>)` — `viajesRestaurados` son los ids rechazados por estar aprobados en la última sincronización (los guarda el worker en `Preferencias`; se limpian al mostrarse).
  - `SyncWorker` (CoroutineWorker) llama `sincronizar()`; `SinConexion`/`Falla` → `Result.retry()` con backoff exponencial.

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun sube_enOrden_viajesAntesQueHijos()          // registra el orden de llamadas al fake remoto
  @Test fun reintento_noDuplica()                         // sincronizar() dos veces → fake remoto tiene 1 fila por id
  @Test fun subidaFallaAMitad_reintentaSinDuplicar()      // remoto lanza IOException en gastos → viajes marcados, gastos siguen pendientes; segundo intento completa; 1 fila por id
  @Test fun cambioDuranteSubida_quedaPendiente()          // el fake modifica modificadoLocal mientras sube → sigue pendiente
  @Test fun viajeAprobado_rechazado_seDescartaYSeInforma() // subirViaje → RECHAZADO_APROBADO → descartarViaje llamado; ResultadoSync.Ok.rechazados contiene el id
  @Test fun bajada_noPisaPendientes()                     // local pendiente + remoto distinto → queda el local
  @Test fun bajada_guardaMarcaDelServidor()               // marca = máximo actualizado_en recibido
  @Test fun adjunto_subeArchivoAntesQueFila()             // rutaRemota se completa antes de subir la fila
  @Test fun sinSesion_noHaceNada()
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar `Sincronizador.sincronizar()`**: (1) si `choferId()` es null → `Ok(0,0,[])`; (2) viajes uno a uno con `subirViaje`, marcar OK, descartar rechazados; (3) revisiones, eventos, gastos, novedades con `subirLote` (upsert `onConflict = "id"`) y marcar cada ítem; (4) adjuntos: `subirArchivo` a `adjuntos/{viajeId}/{id}.jpg|png`, guardar `rutaRemota`, luego la fila; (5) `bajarDesde(marca)` y `aplicarRemotos`; guardar la marca devuelta. `IOException`/`HttpRequestException` → `SinConexion`. Implementar `OrigenLocalRoom` (sobre `SyncDao`, en `@Transaction` para aplicar remotos) y `DestinoSupabase` (detecta `RECHAZADO_APROBADO` por el mensaje `VIAJE_APROBADO` del `RestException`).
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Commit** — `feat(sync): sincronización offline con cola, reintentos y bajada incremental`

---

### Task 7: Sistema visual

**Files:**
- Create: `…/ui/theme/Color.kt`, `Tipografia.kt`, `Tema.kt`, `…/ui/componentes/LetreroRuta.kt`, `CaminoRuta.kt`, `BotonPrincipal.kt`, `IndicadorSync.kt`, `CampoTexto.kt`, `EstadoVacio.kt`, `HojaInferior.kt`
- Modify: `app/src/main/res/values/` (certificados de Google Fonts `font_certs.xml`)

**Interfaces:**
- Produces:
  - `@Composable fun TemaBitacora(claro: Boolean = false, content: @Composable () -> Unit)`; `object ColoresBitacora` con los 6 tokens del spec + `rojoPare = #C8102E` para errores/alertas; esquema claro: fondo `#F3F2EC`, superficie `#FFFFFF`, texto `#1C1F22`, mismos verde y ámbar.
  - `val Barlow`, `val BarlowCondensed` (Google Fonts descargables); `Typography` con `display*`/`headline*` en Barlow Condensed y `body*`/`label*` en Barlow.
  - `@Composable fun LetreroRuta(ruta: String?, origen: String, destino: String, detalle: String, kmActual: Int?, kmTotal: Int?, onClick: () -> Unit)` — letrero verde con borde blanco interior, escudo con `ruta` si existe, barra de avance ámbar sobre línea discontinua.
  - `@Composable fun CaminoRuta(hitos: List<Hito>, animarEntrada: Boolean)` con `data class Hito(val km: Int?, val hora: String, val titulo: String, val detalle: String?, val actual: Boolean)` — camino vertical; al entrar, la línea ámbar se dibuja hasta el hito actual (`Animatable` 0→1, ~900 ms, desactivada si `animarEntrada=false` o el sistema quitó animaciones); hitos nuevos aparecen con `animateItem`.
  - `@Composable fun BotonPrincipal(texto: String, onClick: () -> Unit, cargando: Boolean = false, habilitado: Boolean = true)` (ámbar, alto 56 dp, texto 17 sp).
  - `@Composable fun IndicadorSync(estado: EstadoSync)` — textos exactos: `"Sin señal. $n registros se enviarán solos."` (`"1 registro se enviará solo."` en singular), `"Enviando…"`, `"Todo enviado"`; transición con `AnimatedContent`.
  - `CampoTexto` (etiqueta, error bajo el campo, teclado numérico opcional), `EstadoVacio(titulo, texto, accion?)`, `HojaInferior` (ModalBottomSheet con estilo).

- [ ] **Step 1: Implementar tema y componentes** con `@Preview` oscuros y claros de cada componente usando los datos de la maqueta (Santiago a Concepción, km 214 de 500, hitos 0/142/151/214).
- [ ] **Step 2: Revisar las previews** en Android Studio contra `.superpowers/brainstorm/921-1794362822/content/estilo-visual-v3.html`; ajustar hasta que coincidan.
- [ ] **Step 3: Compilar** — `gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** — `feat(ui): sistema visual Señalética de autopista`

---

### Task 8: Autenticación, sesión offline y navegación por rol

**Files:**
- Create: `…/data/repo/AuthRepositorio.kt`, `…/ui/navegacion/Destinos.kt`, `NavegacionRaiz.kt`, `…/feature/auth/LoginScreen.kt`, `LoginViewModel.kt`, `RegistroScreen.kt`, `RegistroViewModel.kt`, `RecuperacionScreen.kt`, `RecuperacionViewModel.kt`
- Modify: `…/di/AppContainer.kt`, `…/MainActivity.kt`
- Test: `…/data/repo/AuthRepositorioTest.kt`, `…/feature/auth/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `Preferencias`, `SesionCifrada`, `crearSupabase` (Task 5); `AppDatabase.borrarTodo()` (Task 4); `Validaciones` (Task 3).
- Produces:
  - `sealed interface EstadoSesion { data object Cargando; data object SinSesion; data class Activa(val perfil: Perfil) }`
  - `interface AuthRepositorio { val estado: Flow<EstadoSesion>; suspend fun iniciarSesion(email: String, contrasena: String): Result<Perfil>; suspend fun registrar(nombre: String, email: String, contrasena: String): Result<Perfil>; suspend fun enviarCodigo(email: String): Result<Unit>; suspend fun restablecer(email: String, codigo: String, nueva: String): Result<Unit>; suspend fun cerrarSesion() }` — impl `AuthRepositorioSupabase(auth: AuthApi, fuentePerfil, prefs, db)` con las dependencias de red detrás de interfaces para poder testear.
  - Regla: `estado` es `Activa(perfil)` si hay perfil en `Preferencias` y sesión guardada, aunque la renovación falle por red; `SinSesion` solo si no hay sesión o el usuario cerró sesión.
  - `cerrarSesion()` borra Room, `Preferencias` y la sesión.
  - Destinos Navigation 3 (`@Serializable … : NavKey`): `Login, Registro, Recuperacion, Inicio, MisViajes, NuevoViaje, HojaDeRuta(viajeId: String), Entrega(viajeId: String), Resumen(viajeId: String), MiPerfil, AdminViajes, AdminDetalle(viajeId: String), AdminReportes, AdminCamiones`.
  - `NavegacionRaiz`: `SinSesion` → pila `[Login]`; `Activa(rol=CHOFER)` → `[Inicio]` con barra inferior Inicio / Mis viajes / Perfil; `Activa(rol=ADMIN)` → `[AdminViajes]` con barra Viajes / Reportes / Camiones / Perfil.

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun sesionGuardada_sinRed_quedaActiva()     // fake auth: hay sesión, refresh lanza IOException; prefs con perfil → Activa
  @Test fun sinSesion_esSinSesion()
  @Test fun cerrarSesion_borraDatosLocales()          // verifica llamado a db.borrarTodo() y prefs.limpiar()
  @Test fun login_emailInvalido_noLlamaAlServidor()   // LoginViewModel muestra "Escribe un correo válido."
  @Test fun login_credencialesMalas_muestraMensaje()  // error 400 → "Correo o contraseña incorrectos."
  @Test fun login_sinRed_muestraMensaje()             // "Necesitas conexión para iniciar sesión la primera vez."
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar** repositorio, ViewModels y pantallas. Registro: nombre ≥ 3, email válido, `contrasenaValida`, confirmación igual; envía `nombre_completo` en metadata. Recuperación en dos pasos: correo → "Enviar código"; código de 6 dígitos + nueva contraseña → "Cambiar contraseña" (`verifyEmailOtp(type = RECOVERY)` y `updateUser { password }`). Tras login exitoso: guardar perfil, `ProgramadorSync.alGuardar()` para la primera bajada, y bajar camiones.
- [ ] **Step 4: Ver pasar** → PASS. Prueba manual en emulador: registrar chofer, cerrar app, modo avión, reabrir → entra al Inicio.
- [ ] **Step 5: Commit** — `feat(auth): login, registro, recuperación por código y sesión offline`

---

### Task 9: Inicio del chofer y lista de viajes

**Files:**
- Create: `…/data/repo/ViajesRepositorio.kt`, `…/feature/inicio/InicioScreen.kt`, `InicioViewModel.kt`, `…/feature/viajes/MisViajesScreen.kt`, `MisViajesViewModel.kt`
- Test: `…/feature/inicio/InicioViewModelTest.kt`

**Interfaces:**
- Consumes: DAOs (Task 4), `CalculosViaje`, `Formatos` (Task 3), `ProgramadorSync.estado` (Task 6), componentes (Task 7).
- Produces:
  - `interface ViajesRepositorio` (impl `ViajesRepositorioRoom(db, reloj, programador)`): `fun viajes(choferId): Flow<List<ViajeEntity>>`, `fun enCurso(choferId): Flow<ViajeEntity?>`, `fun viaje(id): Flow<ViajeEntity?>`, `fun detalle(id): Flow<DetalleViaje>`, `fun camiones(): Flow<List<CamionEntity>>`, `suspend fun crearViaje(datos: DatosNuevoViaje, revision: DatosRevision): Result<String>`, `suspend fun registrarEvento(...)`, `registrarGasto(...)`, `registrarNovedad(...)`, `cerrarDescanso(eventoId)`, `finalizar(...)`, `guardarAdjunto(...)`, `editarViaje(...)` — cada escritura fija `modificadoLocal = reloj.ahora()`, `syncPendiente = true` y llama `programador.alGuardar()`. Las firmas exactas de escritura se definen en Tasks 10–12; esta task agrega solo las de lectura.
  - `data class DetalleViaje(val viaje: ViajeEntity?, val revision: RevisionEntity?, val eventos: List<EventoEntity>, val gastos: List<GastoEntity>, val novedades: List<NovedadEntity>, val adjuntos: List<AdjuntoEntity>, val camion: CamionEntity?)`
  - `data class EstadoInicio(val nombre: String, val enCurso: ViajeEntity?, val kmActual: Int?, val alerta: AlertaHoras, val manejoContinuoMs: Long, val kmMes: Int, val gastosMes: Long, val viajesMes: Int, val sync: EstadoSync)`

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun sinViajeEnCurso_muestraCrearViaje()       // enCurso == null
  @Test fun conViajeEnCurso_calculaKmActual()          // kmActual = km del último evento con km, o kmInicial
  @Test fun alertaProxima_cuandoLleva4h30()            // reloj fijo + inicio hace 4h30 → PROXIMA
  @Test fun resumenMes_sumaSoloMesActual()
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar.** Inicio: saludo, `IndicadorSync`, `LetreroRuta` del viaje en curso (o `EstadoVacio` "Aún no tienes un viaje en curso" + "Crear viaje"), `BotonPrincipal("Registrar parada")`, fila Combustible / Peaje / Novedad (abren las hojas de Task 11), aviso si `sync.viajesRestaurados` no está vacío ("Un viaje ya aprobado no se puede modificar. Se restauró la versión aprobada."), aviso de horas (ámbar en `PROXIMA`: "Te quedan 30 min para descansar"; rojo en `EXCEDIDA`: "Llevas más de 5 horas manejando. Toma un descanso."), resumen del mes en una línea. Mis viajes: lista con `animateItem`, filtro En curso / Finalizados, chip de validación (Pendiente / Aprobado / Observado).
- [ ] **Step 4: Ver pasar** → PASS; revisar en emulador contra la maqueta.
- [ ] **Step 5: Commit** — `feat(chofer): inicio y lista de viajes`

---

### Task 10: Nuevo viaje con revisión del camión

**Files:**
- Create: `…/feature/viajes/NuevoViajeScreen.kt`, `NuevoViajeViewModel.kt`
- Modify: `…/data/repo/ViajesRepositorio.kt`
- Test: `…/feature/viajes/NuevoViajeViewModelTest.kt`

**Interfaces:**
- Produces:
  - `data class DatosNuevoViaje(val camionId: String, val cliente: String, val origen: String, val destino: String, val ruta: String?, val tipoCarga: String, val pesoKg: Double, val refrigeracion: Boolean, val precio: Long, val kmInicial: Int, val observaciones: String?)`
  - `data class DatosRevision(val neumaticos: Boolean, val luces: Boolean, val frenos: Boolean, val niveles: Boolean, val documentos: Boolean, val comentario: String?)`
  - `ViajesRepositorio.crearViaje(datos, revision): Result<String>` — en una transacción crea viaje (`EN_CURSO`, `fechaSalida = ahora`), revisión y evento `INICIO` (km = `kmInicial`, con ubicación si está disponible); falla con `ViajeEnCursoException(idExistente)` si ya hay uno en curso.

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun crearViaje_conViajeEnCurso_noCreaOtro()   // estado.error == "Ya tienes un viaje en curso." y ofrece abrirlo
  @Test fun camposObligatorios()                       // sin cliente → error "Escribe el cliente."
  @Test fun peso_aceptaPuntoDeMiles()                  // "18.500" → 18500.0
  @Test fun revision_conItemNoOk_permiteSalirConComentario() // no bloquea; guarda false
  @Test fun crear_marcaPendienteYProgramaSync()
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar** formulario en 3 pasos con indicador de paso (los pasos sí son secuencia): (1) Viaje: cliente, origen, destino, ruta, carga, peso, refrigeración, precio; (2) Camión: selector de patentes (`camiones()`), km inicial, observaciones; (3) Revisión: 5 casillas (Neumáticos, Luces, Frenos, Niveles de aceite y agua, Documentos al día) + comentario → `BotonPrincipal("Iniciar viaje")`. Al crear, navegar a `HojaDeRuta(id)`.
- [ ] **Step 4: Ver pasar** → PASS; probar en modo avión.
- [ ] **Step 5: Commit** — `feat(chofer): nuevo viaje con revisión del camión`

---

### Task 11: Hoja de ruta y registros en ruta (GPS, combustible, peaje, novedad, descanso)

**Files:**
- Create: `…/data/repo/UbicacionRepositorio.kt`, `…/feature/ruta/HojaDeRutaScreen.kt`, `HojaDeRutaViewModel.kt`, `RegistrarParadaSheet.kt`, `RegistrarGastoSheet.kt`, `RegistrarNovedadSheet.kt`
- Modify: `…/data/repo/ViajesRepositorio.kt`, `AndroidManifest.xml` (sin cambios de permisos: ya declarados)
- Test: `…/feature/ruta/HojaDeRutaViewModelTest.kt`

**Interfaces:**
- Produces:
  - `data class Ubicacion(val lat: Double, val lon: Double, val direccion: String?)`; `interface UbicacionRepositorio { suspend fun actual(timeoutMs: Long = 8_000): Ubicacion? }` — impl con `FusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY)` + `Geocoder` (si falla, `direccion = null`); devuelve `null` sin permiso o sin fijación. Pedir permiso siguiendo la skill `android-permissions-security` (explicar antes: "Usamos tu ubicación solo para marcar dónde registras cada parada.").
  - `ViajesRepositorio.registrarEvento(viajeId: String, tipo: TipoEvento, km: Int?, nota: String?): Result<String>` (toma ubicación internamente), `cerrarDescanso(eventoId: String): Result<Unit>`, `registrarGasto(viajeId, tipo: TipoGasto, monto: Long, litros: Double?, descripcion: String?): Result<String>`, `registrarNovedad(viajeId, tipo: TipoNovedad, gravedad: Gravedad, descripcion: String): Result<String>`.
  - Los eventos sin `direccion` se completan en el `Sincronizador` con `Geocoder` cuando hay red (paso adicional antes de subir eventos).

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun registrarParada_sinUbicacion_guardaSinCoordenadas()  // fake ubicación → null; evento guardado con lat/lon null
  @Test fun descanso_abiertoYCerrado()                            // registrar DESCANSO, luego cerrar → fechaFin = ahora
  @Test fun combustible_requiereLitrosYMonto()                    // "Escribe los litros cargados."
  @Test fun hitos_ordenadosPorHora_conActualAlFinal()
  @Test fun viajeAprobado_noPermiteRegistrar()                    // estado.soloLectura == true
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar.** Hoja de ruta: título "Hoja de ruta", patente y hora de salida, `CaminoRuta(animarEntrada = true)` con eventos, gastos y novedades mezclados por hora; aviso de horas; `BotonPrincipal("Registrar parada")` y acción secundaria "Finalizar viaje". La hoja de parada ofrece Parada / Descanso / Carga / Descarga y km opcional; un descanso abierto muestra "Terminar descanso". Gastos: Combustible (litros + monto), Peaje (monto), Otro. Novedad: tipo, gravedad, descripción.
- [ ] **Step 4: Ver pasar** → PASS. En emulador: modo avión + ubicación simulada; registrar los 4 tipos; ver el hito caer en el camino.
- [ ] **Step 5: Commit** — `feat(chofer): hoja de ruta y registros en ruta con GPS`

---

### Task 12: Entrega, foto o firma, y resumen del viaje

**Files:**
- Create: `…/feature/entrega/EntregaScreen.kt`, `EntregaViewModel.kt`, `PanelFirma.kt`, `…/feature/viajes/ResumenScreen.kt`, `ResumenViewModel.kt`, `app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider `${applicationId}.archivos`), `ViajesRepositorio.kt`
- Test: `…/feature/entrega/EntregaViewModelTest.kt`

**Interfaces:**
- Produces:
  - `ViajesRepositorio.finalizar(viajeId: String, kmFinal: Int, adjunto: NuevoAdjunto?): Result<Unit>` — valida `kmCoherentes`; registra evento `ENTREGA` (con ubicación) y `FIN`; fija `fechaLlegada`, `kmFinal`, `estado = FINALIZADO`.
  - `data class NuevoAdjunto(val tipo: TipoAdjunto, val archivo: File, val nombreReceptor: String?)`; los archivos viven en `filesDir/adjuntos/{viajeId}/{id}.jpg|png`.
  - `@Composable fun PanelFirma(onFirma: (Bitmap) -> Unit)` — trazo con `pointerInput`, botones "Borrar" y "Usar firma"; exporta PNG.
  - Foto con `ActivityResultContracts.TakePicture()` y URI del FileProvider (no se declara permiso CAMERA).

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun kmFinalMenor_muestraError()                 // "El km final no puede ser menor que el inicial (1.000 km)."
  @Test fun finalizar_sinAdjunto_permitido()
  @Test fun finalizar_creaEventosEntregaYFin()
  @Test fun resumen_muestraValoresDeCalculosViaje()      // usa CalculosViaje.resumen
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar.** Entrega: km final, nombre de quien recibe, "Tomar foto de la guía" o "Pedir firma" (uno u otro, opcional), `BotonPrincipal("Finalizar viaje")` → `Resumen(id)`. Resumen: km recorridos, duración, gastos por tipo, ganancia, litros estimados vs. reales (y en pesos), estado de validación y comentario del administrador si lo hay; si está `OBSERVADO`, botón "Corregir viaje" que abre edición de datos del viaje.
- [ ] **Step 4: Ver pasar** → PASS. En emulador, modo avión: finalizar con foto; quitar modo avión; verificar archivo en el bucket `adjuntos` de Supabase.
- [ ] **Step 5: Commit** — `feat(chofer): entrega con foto o firma y resumen del viaje`

---

### Task 13: Administrador — viajes en vivo, aprobación y camiones

**Files:**
- Create: `…/data/repo/AdminRepositorio.kt`, `…/feature/admin/AdminViajesScreen.kt`, `AdminViajesViewModel.kt`, `AdminDetalleScreen.kt`, `AdminDetalleViewModel.kt`, `AdminCamionesScreen.kt`, `AdminCamionesViewModel.kt`
- Test: `…/feature/admin/AdminDetalleViewModelTest.kt`

**Interfaces:**
- Produces:
  - `interface AdminRepositorio { fun viajesEnVivo(filtro: FiltroViajes): Flow<List<ResumenViajeDto>>; suspend fun detalle(viajeId: String): DetalleViajeRemoto; suspend fun validar(viajeId: String, aprobado: Boolean, comentario: String?): Result<Unit>; suspend fun urlAdjunto(ruta: String): String; fun camiones(): Flow<List<CamionDto>>; suspend fun guardarCamion(c: CamionDto): Result<Unit> }`
  - `viajesEnVivo`: consulta `resumen_viajes` y se vuelve a emitir con cada cambio de Realtime en `viajes` (canal `admin-viajes`); `FiltroViajes(choferId: String?, estado: EstadoViaje?, validacion: EstadoValidacion?, desde: Long?, hasta: Long?)`.
  - `DetalleViajeRemoto` con el viaje, revisión, eventos, gastos, novedades, adjuntos (URL firmada 1 h) y la marca `excedioHoras: Boolean` (calculada con `CalculosViaje.manejoContinuoMs` sobre los eventos en cada punto).

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun observar_sinComentario_muestraError()        // "Escribe qué debe corregir el chofer."
  @Test fun aprobar_viajeEnCurso_noPermitido()           // botón deshabilitado + texto "Solo se aprueban viajes finalizados."
  @Test fun excedioHoras_seMarca()
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar.** Lista con filtros y marca de horas excedidas; si no hay red: `EstadoVacio("Sin conexión", "Las pantallas de administración necesitan internet.", "Reintentar")`. Detalle: misma `CaminoRuta` (sin animación de entrada), foto/firma, botones "Aprobar" y "Observar" (pide comentario). Camiones: lista + formulario (patente validada, marca, modelo, año, rendimiento km/L, activo).
- [ ] **Step 4: Ver pasar** → PASS. Prueba de dos dispositivos (emulador chofer + teléfono admin): el chofer finaliza con modo avión, lo quita, el viaje aparece solo en el admin; el admin observa; el chofer ve "Observado" tras sincronizar.
- [ ] **Step 5: Commit** — `feat(admin): viajes en vivo, aprobación de bitácoras y camiones`

---

### Task 14: Reportes mensuales y perfil

**Files:**
- Create: `…/feature/admin/AdminReportesScreen.kt`, `AdminReportesViewModel.kt`, `…/feature/perfil/PerfilScreen.kt`, `PerfilViewModel.kt`, `…/domain/Reportes.kt`
- Test: `…/domain/ReportesTest.kt`

**Interfaces:**
- Produces:
  - `data class FilaReporte(val clave: String, val viajes: Int, val km: Int, val gastos: Long, val ganancia: Long)`; `data class Reporte(val porChofer: List<FilaReporte>, val porCamion: List<FilaReporte>, val totales: FilaReporte, val aprobados: Int, val pendientes: Int, val observados: Int)`
  - `object Reportes { fun mensual(filas: List<ResumenViajeDto>, anio: Int, mes: Int): Reporte }` — solo viajes `finalizado` con `fecha_salida` en el mes.

- [ ] **Step 1: Tests**
  ```kotlin
  @Test fun mensual_cuentaSoloFinalizadosDelMes()
  @Test fun mensual_agrupaPorChoferYCamion()
  @Test fun mensual_cuentaValidaciones()
  ```
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar.** Reportes: selector de mes, totales en cifras grandes (Barlow Condensed), barras horizontales simples por chofer y por camión (km y ganancia). Perfil: nombre, correo, rol, `IndicadorSync` + "Sincronizar ahora", interruptor "Tema claro", "Cerrar sesión" (confirma: "Se borrarán los datos guardados en este teléfono. Los registros sin enviar se perderán." si hay pendientes).
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Commit** — `feat: reportes mensuales y perfil`

---

### Task 15: Pulido, rendimiento y ensayo de la demo

**Files:**
- Modify: pantallas según hallazgos; `app/build.gradle.kts` (firma de release con keystore local fuera de Git)
- Create: `docs/demo.md` (guion de la demo)

- [ ] **Step 1: Revisión edge-to-edge** con la skill `edge-to-edge`: ningún botón bajo la barra de navegación, teclado (IME) no tapa campos.
- [ ] **Step 2: Revisión de rendimiento** con la skill `jetpack-compose-performance`: listas con `key`, estados estables, sin recomposiciones por frame en `CaminoRuta`.
- [ ] **Step 3: Revisión de seguridad** con la skill `android-permissions-security` sobre el manifiesto (FileProvider no exportado, sin componentes exportados salvo `MainActivity`).
- [ ] **Step 4: Recorrido completo en modo avión** en emulador y en un teléfono real siguiendo `docs/demo.md`: crear viaje → revisión → parada → descanso → combustible → peaje → novedad → entrega con foto → resumen → quitar modo avión → "Todo enviado" → aparece en el admin → aprobar. Anotar y corregir cada falla.
- [ ] **Step 5: Todas las pruebas** — `gradlew.bat testDebugUnitTest connectedDebugAndroidTest` → PASS.
- [ ] **Step 6: APK de entrega** — `gradlew.bat assembleRelease` → `app-release.apk` firmado.
- [ ] **Step 7: Commit** — `chore: pulido final y guion de demo`

---

### Task 16: Informe corregido

**Files:**
- Create: `informe 2.0 taller de sistemas de la informacion.docx` (el original 1.1 queda intacto)
- Create: `docs/diagramas/` (modelo entidad-relación y arquitectura como imágenes para el informe)

- [ ] **Step 1: Preguntar al usuario** si la empresa del informe es real o ficticia y sus datos (rubro, tamaño, ubicación), y los roles de cada integrante para "Organización del grupo".
- [ ] **Step 2: Aplicar los ajustes de la sección 10 del spec** usando la skill `docx` (editar el XML del original para conservar formato): título "Sistema Móvil…", objetivos y alcance con Supabase, recursos corregidos (Android Studio, Kotlin + Compose, PostgreSQL/Supabase, costos reales), riesgo de publicación en Play Store, Google Maps → GPS del teléfono, módulos según lo construido.
- [ ] **Step 3: Agregar secciones** de arquitectura (diagrama de la sección 4 del spec), modelo de datos (diagrama ER), funcionamiento sin señal (reglas de la sección 5) y seguridad (sección 6).
- [ ] **Step 4: Verificar** renderizando a PDF y revisando cada página (procedimiento de la skill `docx`).
- [ ] **Step 5: Commit** — `docs: informe 2.0 corregido`
