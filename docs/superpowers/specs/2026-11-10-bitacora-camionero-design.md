# Bitácora del Camionero — Documento de diseño

Fecha: 10 de noviembre de 2026 · Entrega: 25 de noviembre de 2026 (app funcionando + informe)
Ramo: Taller de Sistemas de Información (UTEM, 2.º semestre 2026) · Grupo de 5 integrantes

## 1. Intención

Una app Android para que los choferes de camión lleven su bitácora de viaje desde el celular, reemplazando
las anotaciones en papel y las planillas Excel sueltas. El administrador de la empresa ve en tiempo real
(cuando hay conexión) lo que registran los choferes, aprueba las bitácoras y obtiene reportes.

Es la evolución de "Diario del Camionero", una app que el autor hizo en mayo de 2025 (Kotlin + Compose,
4 pantallas que no guardaban nada). El informe del grupo ("informe 1.1") describe el proyecto como la
mejora de ese sistema existente; esta app tiene que cumplir lo que ese informe promete, con los ajustes
de la sección 8.

**Por qué importa:** el proyecto se evalúa el 25 de noviembre. El grupo quiere mostrar una app que se
vea claramente superior a la versión anterior en aspecto, animaciones, seguridad y calidad técnica.

### Qué es éxito

Dicho por el usuario: *"que la app funcione completa sin señal y se vea profesional"*.

- **Funciona completa sin señal.** Todo lo que hace el chofer funciona en modo avión y se sincroniza
  solo al volver la conexión, sin perder ni duplicar registros. Es la columna vertebral del diseño, no
  una función agregada.
- **Se ve profesional.** Un sistema visual coherente en todas las pantallas, mensajes claros, estados
  vacíos útiles, animaciones fluidas, nada tapado por las barras del sistema. Preferir menos pantallas
  pulidas que muchas a medio hacer.

Momento de la demo (propuesta): teléfono en modo avión frente al profesor → se registra un viaje
completo → se quita el modo avión → el aviso "3 registros por enviar" cambia a "Todo enviado" → el
viaje aparece en la cuenta del administrador en otro teléfono.

### Anti-objetivos (fracaso aunque "funcione")

- Perder o duplicar registros al sincronizar.
- Cualquier pantalla del chofer que falle o se quede cargando sin señal.
- Que se vea como una plantilla genérica de Android.

## 2. Usuarios

- **Chofer:** registra sus viajes en ruta, muchas veces sin señal, con el teléfono en un soporte de la
  cabina o en la mano durante paradas. Necesita botones grandes, poco texto que escribir y cero
  sorpresas.
- **Administrador:** trabaja en oficina, con conexión. Revisa viajes de todos, aprueba u observa
  bitácoras, ve reportes y administra camiones. Usa **la misma app** con un menú distinto (no hay panel
  web).

## 3. Alcance (decidido por el usuario)

### Chofer — al salir
- Datos del viaje: cliente, origen, destino, tipo de carga, peso (kg), precio, requiere refrigeración,
  observaciones (los campos de la app 2025).
- Elegir el camión de una lista de patentes.
- Kilometraje inicial (odómetro).
- Revisión del camión: checklist rápido antes de salir.

### Chofer — en ruta
- Paradas y descansos, con hora y ubicación GPS tomadas automáticamente.
- Cargas de combustible (litros y monto).
- Peajes (monto).
- Novedades: incidente, falla mecánica, retraso, accidente, clima, otro; con gravedad (baja, media, alta).

### Chofer — al llegar
- Kilometraje final.
- Hora de entrega, con ubicación.
- Foto o firma de quien recibe.
- Resumen del viaje: km recorridos, gastos totales por tipo, ganancia (precio − gastos), duración,
  combustible estimado vs. real.

### Administrador
- Lista de viajes de todos los choferes, con filtros (chofer, estado, validación, fechas).
- Aprobar u observar bitácoras (con comentario cuando se observa).
- Reportes mensuales: km, gastos y ganancias por chofer y por camión.
- Alerta de horas de manejo continuas.
- Administrar camiones (necesario para que exista la lista de patentes; ver 7).

### Base
- Iniciar sesión, registrarse, recuperar contraseña.
- Modo sin señal (sección 5).
- Perfil: datos del usuario, estado de sincronización, cerrar sesión, tema claro/oscuro.

### Fuera de alcance
- Panel web (el título "Sistema Web" del informe se corrige).
- Seguimiento GPS continuo en tiempo real; solo ubicaciones por evento.
- Conexión al vehículo (OBD-II), pagos, facturación, iOS.
- Estimación de peajes con la API de Google Maps (los peajes se ingresan a mano).
- Publicación en producción en Google Play (requiere 14 días de prueba cerrada con 12 testers); se
  entrega APK y, si alcanza, la pista de prueba interna.

## 4. Arquitectura (decidida)

```
 ┌──────────────── App Android (Kotlin + Jetpack Compose, MVVM) ────────────────┐
 │  Pantallas (Compose) → ViewModels → Repositorios                             │
 │                                       │                                      │
 │                     Room (SQLite local) ◄── fuente de verdad del chofer      │
 │                                       │                                      │
 │          SyncWorker (WorkManager) ── sube pendientes / baja cambios          │
 └───────────────────────────────────────┼──────────────────────────────────────┘
                                         │ HTTPS (supabase-kt)
 ┌───────────────────────────── Supabase ┼──────────────────────────────────────┐
 │  Auth (correo + contraseña, hash bcrypt)   PostgREST (API REST automática)   │
 │  PostgreSQL con RLS (seguridad por fila)   Storage (fotos/firmas)            │
 └──────────────────────────────────────────────────────────────────────────────┘
```

- **App:** Kotlin, Jetpack Compose, Material 3, MVVM. Paquete `com.sistemasdeinformacion.proyecto`
  (el mismo de 2025). minSdk 26 (Android 8.0), como pide el informe.
- **Backend:** Supabase en vez de una API propia en Railway. Es PostgreSQL real, así que el informe sigue
  siendo válido ("API REST + base SQL en la nube"), y ahorra la semana que costaría programar la API.
  Plan gratuito.
- **Local:** Room como fuente de verdad para todo lo del chofer. Las pantallas del chofer leen siempre de
  Room, nunca directamente de la red.
- **Sincronización:** WorkManager, en segundo plano, cuando hay red.
- **Administrador:** lee directo de Supabase (requiere conexión); muestra un estado claro si no hay red.

Los borradores ya escritos (`supabase/schema.sql`, Gradle, `Enums.kt`, `Entities.kt`) son punto de
partida y se ajustan a este documento.

## 5. Modo sin señal (núcleo)

Reglas que no se negocian:

1. **IDs generados en el teléfono (UUID).** Cada viaje, evento, gasto, novedad, revisión y adjunto nace
   con su ID definitivo, sin esperar al servidor.
2. **Cola de pendientes.** Cada registro local tiene `syncPendiente` y una marca `modificadoLocal`.
   Al sincronizar, se suben los pendientes con *upsert por id* (insertar o actualizar). Reintentar nunca
   duplica.
3. **No marcar como enviado algo que cambió durante el envío:** solo se limpia `syncPendiente` si
   `modificadoLocal` no cambió desde que empezó el envío.
4. **Orden de subida:** viajes → revisiones → eventos → gastos → novedades → adjuntos (por las llaves
   foráneas).
5. **Bajada de cambios:** se piden las filas con `actualizado_en` (fijado por el servidor) mayor que la
   última marca recibida. Si una fila local tiene cambios pendientes, gana la local hasta que se suba.
   Así llegan al chofer, por ejemplo, la aprobación u observación del administrador.
6. **Borrado lógico** (`eliminado = true`), para que los borrados también se sincronicen.
7. **Fotos y firmas:** se guardan como archivo en el almacenamiento privado de la app y se suben a
   Supabase Storage al haber señal; la fila guarda la ruta remota cuando ya está subida.
8. **Sesión offline:** iniciar sesión requiere internet la primera vez en ese teléfono. Después, la sesión
   y el perfil (nombre, rol) quedan guardados y la app abre sin señal. Si el token vence sin señal, el
   chofer sigue trabajando y los envíos esperan a que se renueve.
9. **GPS sin internet:** se guardan latitud y longitud siempre. La dirección legible (geocodificación) se
   intenta en el momento y, si no hay red, se completa al sincronizar.
10. **Visible para el chofer:** un indicador permanente: "Sin señal. N registros se enviarán solos" /
    "Enviando…" / "Todo enviado". Disparo inmediato de sincronización al volver la red.

## 6. Seguridad (decidida)

- Contraseñas gestionadas por Supabase Auth (hash bcrypt); nunca se guardan en el modelo de la app
  (la app 2025 tenía `password` en `Usuario`).
- RLS en todas las tablas: el chofer solo lee/escribe lo suyo; el administrador lee todo.
- Un chofer no puede cambiarse el rol ni aprobar sus propios viajes (trigger en la base).
- El registro siempre crea usuarios con rol chofer; el primer administrador se asigna a mano en Supabase.
- Sesión guardada cifrada con Android Keystore.
- Solo HTTPS; sin respaldo en la nube de la base local ni de la sesión (`allowBackup=false`).
- Permisos de ubicación y cámara pedidos en el momento de usarlos, explicando para qué.
- Validaciones en el formulario y también en la base (CHECK de km, fechas, montos).
- Credenciales de Supabase fuera del repositorio (`local.properties`).

## 7. Modelo de datos

Tablas (PostgreSQL; espejo en Room para lo del chofer):

- `perfiles` (1:1 con `auth.users`): nombre completo, RUT, teléfono, rol (chofer/admin), activo.
- `camiones`: patente, marca, modelo, año, rendimiento km/L, activo. Los administra el administrador.
- `viajes`: chofer, camión, cliente, origen, destino, tipo de carga, peso, refrigeración, precio, fechas
  de salida y llegada, km inicial y final, estado (en curso / finalizado / cancelado), validación
  (pendiente / aprobado / observado), comentario de validación, observaciones.
- `revisiones`: una por viaje; ítems del checklist (sí/no) y comentario.
- `eventos`: inicio, parada, descanso, carga, descarga, entrega, fin; hora de inicio, hora de término
  (para descansos y paradas con duración), lat/long, dirección, km, nota.
- `gastos`: combustible, peaje, alimentación, mantención, otro; monto, litros (combustible), hora.
- `novedades`: tipo, gravedad, descripción, hora, ubicación.
- `adjuntos`: foto o firma de entrega; ruta local, ruta remota, nombre de quien recibe.
- Vista `resumen_viajes` para reportes (km, gastos por tipo, ganancia).

## 8. Diseño visual — dirección "Señalética de autopista" (elegida)

- **Fondo asfalto oscuro** por defecto; tema claro opcional desde el Perfil para pleno sol.
- **Firma visual:** el viaje en curso es un **letrero verde de autopista** (verde señal con borde blanco
  interior y escudo de ruta, p. ej. "5 SUR"). Es el único elemento audaz; el resto queda sobrio.
- **Ámbar** solo para la acción principal de cada pantalla y la posición actual.
- **Tipografía:** Barlow para texto; Barlow Condensed para rutas, kilómetros y montos.
- **Hoja de ruta:** el detalle del viaje es un camino vertical con hitos kilométricos; el hito actual en
  ámbar.
- **Movimiento:** un único momento orquestado (al abrir un viaje, el camino se dibuja hasta el hito
  actual). El resto responde a acciones: el hito nuevo cae en su lugar al registrar; el indicador de
  sincronización cambia de estado con transición. Respetar "reducir movimiento" del sistema.
- **Texto:** frases cortas, verbos claros ("Registrar parada", "Finalizar viaje"), errores que dicen qué
  pasó y cómo seguir, estados vacíos que invitan a actuar.
- Paleta base (referencia de las maquetas): asfalto `#1C1F22`, superficie `#26292D`, verde señal
  `#0E6B4C`, ámbar `#F2A900`, blanco reflectante `#F3F2EC`, gris berma `#A9AFB6`.

Maquetas de referencia: `.superpowers/brainstorm/921-1794362822/content/estilo-visual-v3.html`.

## 9. Restricciones

- **Quién construye:** Claude construye la app completa y corrige el informe, trabajando con el usuario.
  Los compañeros revisan, prueban en sus teléfonos y dan feedback; solo se les delegan tareas menores
  (revisiones, ajustes que no les calcen). El plan es una secuencia para un solo constructor.

- 15 días hasta la entrega; app y versión corregida del informe el mismo día.
- Desarrollo en Windows con Android Studio y emulador; demo en teléfonos Android reales del grupo.
- Costo $0 (planes gratuitos de Supabase).
- El informe es la base del proyecto; lo que cambie respecto de él se corrige en el informe.

## 10. Ajustes al informe que derivan de este diseño

- Título: "Sistema Móvil…" en vez de "Sistema Web…".
- Backend: Supabase (PostgreSQL + Auth + API REST + Storage) en vez de API propia en Railway.
- Recursos: Android Studio (no VS Code), Kotlin + Compose, PostgreSQL, costos reales.
- Publicación: APK / prueba interna en vez de producción en Play Store; agregarlo como riesgo.
- Google Maps: ubicación por GPS del teléfono; bencina estimada por km × rendimiento × precio.
- Completar secciones vacías (empresa, organización del grupo, número de grupo).

## 11. Reglas de negocio precisadas (decisiones de Claude, para revisar)

Surgieron de la revisión del documento; el usuario no las dictó, son propuestas razonables.

- **Alerta de horas de manejo.** Umbral: 5 horas de manejo continuo (referencia: art. 25 bis del Código
  del Trabajo para choferes de carga interurbana; verificar el texto vigente). Un **descanso** tiene hora
  de inicio y de término; solo un descanso de al menos 30 minutos reinicia el conteo (valor configurable
  en código). La alerta se calcula **en el teléfono**, así avisa al chofer aunque no haya señal (aviso en
  la pantalla de Inicio cuando faltan 30 minutos y al superar el límite). El administrador ve una marca en
  los viajes que superaron el límite, después de sincronizar. Sin notificaciones push.
- **Validación de bitácoras.** Solo se pueden aprobar u observar viajes **finalizados**. Un viaje
  **aprobado** queda de solo lectura para el chofer (bloqueado en la app y rechazado por la base). Un
  viaje **observado** el chofer lo corrige; al guardar vuelve a **pendiente**. Si una edición sin señal
  choca con un viaje que ya fue aprobado, la base la rechaza, la app descarta el cambio local, baja la
  versión del servidor y avisa al chofer.
- **"El administrador lo ve al tiro".** La lista de viajes del administrador se actualiza en vivo con
  Supabase Realtime, con "tirar para actualizar" como respaldo.
- **Correos.** El correo gratuito de Supabase tiene límites estrictos de envío. Por eso: el registro **no
  pide confirmar el correo**, y la recuperación de contraseña usa un **código de 6 dígitos** que se
  escribe en la app (no un enlace). Para la demo se usan correos de los integrantes del grupo. Configurar
  un SMTP gratuito (por ejemplo Brevo o Resend) queda como mejora opcional.
- **Combustible estimado vs. real.** Se compara en **litros**: estimado = km recorridos ÷ rendimiento del
  camión; real = suma de litros cargados. En pesos, el estimado usa el precio promedio por litro de las
  cargas del propio viaje. No se guarda un precio de referencia.
- **Reportes mensuales.** Solo en la app (sin exportar a Excel o PDF). Cuentan los viajes **finalizados**
  del mes, con la cantidad aprobados / pendientes / observados indicada aparte.

## 12. Queda a criterio de quien construye

- Navegación concreta y estructura de paquetes, siguiendo MVVM.
- Componentes y animaciones exactas dentro de la dirección visual.
- Detalles del algoritmo de sincronización mientras cumpla las reglas de la sección 5.
- Pruebas automatizadas: al menos validaciones, cálculos del resumen y la lógica de sincronización.
