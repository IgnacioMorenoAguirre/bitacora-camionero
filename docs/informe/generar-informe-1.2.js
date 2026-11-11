const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, ImageRun, PageBreak, LevelFormat, TableOfContents, Footer,
  PageNumber, VerticalAlign,
} = require("docx");

const RAIZ = "C:/Proyectos/Taller de sistemas de informacion";
const SALIDA = path.join(RAIZ, "informe 1.2 taller de sistemas de la informacion.docx");
const FUENTE = "Arial";
const ANCHO = 9026; // A4 con márgenes de 1440 DXA

const VERDE = "0E6B4C";
const GRIS = "F2F2F2";

// ---------- utilidades ----------
const p = (texto, opts = {}) => new Paragraph({
  spacing: { after: 120, line: 300 },
  alignment: opts.centro ? AlignmentType.CENTER : AlignmentType.JUSTIFIED,
  ...opts.par,
  children: (Array.isArray(texto) ? texto : [texto]).map(t =>
    typeof t === "string" ? new TextRun({ text: t, font: FUENTE, size: 22, ...opts.run }) : t),
});
const b = (t) => new TextRun({ text: t, bold: true, font: FUENTE, size: 22 });
const t = (x) => new TextRun({ text: x, font: FUENTE, size: 22 });
const pendiente = (x) => new TextRun({ text: x, font: FUENTE, size: 22, highlight: "yellow" });
const h1 = (x) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(x)] });
const h2 = (x) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(x)] });
const vineta = (partes, nivel = 0) => new Paragraph({
  numbering: { reference: "vinetas", level: nivel }, spacing: { after: 80, line: 290 },
  children: (Array.isArray(partes) ? partes : [partes]).map(x => typeof x === "string" ? t(x) : x),
});
const salto = () => new Paragraph({ children: [new PageBreak()] });

const bordes = {
  top: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" },
  bottom: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" },
  left: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" },
  right: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" },
};
const sinBordes = {
  top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
};

function tabla(anchos, filas, { encabezado = true } = {}) {
  const total = anchos.reduce((a, c) => a + c, 0);
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: anchos,
    rows: filas.map((fila, i) => new TableRow({
      tableHeader: encabezado && i === 0,
      children: fila.map((celda, j) => new TableCell({
        width: { size: anchos[j], type: WidthType.DXA },
        borders: bordes,
        shading: encabezado && i === 0 ? { fill: VERDE, type: ShadingType.CLEAR, color: "auto" } : undefined,
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        verticalAlign: VerticalAlign.CENTER,
        children: String(celda).split("\n").map(linea => new Paragraph({
          spacing: { after: 0 },
          children: [new TextRun({
            text: linea, font: FUENTE, size: 19,
            bold: encabezado && i === 0, color: encabezado && i === 0 ? "FFFFFF" : undefined,
          })],
        })),
      })),
    })),
  });
}

function imagen(archivo, ancho, alto) {
  return new ImageRun({
    type: "png",
    data: fs.readFileSync(path.join(RAIZ, "docs/capturas", archivo)),
    transformation: { width: ancho, height: alto },
    altText: { title: archivo, description: archivo, name: archivo },
  });
}

function filaCapturas(items) {
  const w = Math.floor(ANCHO / items.length);
  return new Table({
    width: { size: w * items.length, type: WidthType.DXA },
    columnWidths: items.map(() => w),
    rows: [new TableRow({
      children: items.map(([archivo, pie]) => new TableCell({
        width: { size: w, type: WidthType.DXA }, borders: sinBordes,
        children: [
          new Paragraph({ alignment: AlignmentType.CENTER, children: [imagen(archivo, 150, 333)] }),
          new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 60, after: 160 },
            children: [new TextRun({ text: pie, font: FUENTE, size: 17, italics: true })] }),
        ],
      })),
    })],
  });
}

// ---------- portada ----------
const portada = [
  p([new TextRun({ text: "UNIVERSIDAD TECNOLÓGICA METROPOLITANA", font: FUENTE, size: 26, bold: true })], { centro: true }),
  p("Escuela de Informática", { centro: true }),
  p("Taller de Sistemas de Información", { centro: true }),
  p("2do Semestre - 2026", { centro: true }),
  new Paragraph({ spacing: { before: 1800 }, children: [] }),
  p([new TextRun({ text: "INFORME PROPUESTA PROYECTO SI", font: FUENTE, size: 28, bold: true })], { centro: true }),
  p([new TextRun({ text: "Sistema Móvil de Gestión de Bitácora para Control de Operaciones de Camioneros", font: FUENTE, size: 32, bold: true, color: VERDE })], { centro: true }),
  p([new TextRun({ text: "Versión 1.2: incluye avance del prototipo al 11 de noviembre de 2026", font: FUENTE, size: 22, italics: true })], { centro: true }),
  new Paragraph({ spacing: { before: 1800 }, children: [] }),
  p([b("Profesora: "), t("Cindy Betzabe Contador Cisterna")], { centro: true }),
  p([b("Número de grupo: "), pendiente("[por completar]")], { centro: true }),
  p([b("Integrantes:")], { centro: true }),
  ...["Nicolás Sepúlveda Catalán", "Francisco Jeria Guerra", "Benjamín Hernández", "Claudio Pizarro Cerda", "Ignacio Moreno Aguirre"]
    .map(n => p(n, { centro: true, par: { spacing: { after: 40 } } })),
  new Paragraph({ spacing: { before: 900 }, children: [] }),
  p("Santiago, Chile. Noviembre de 2026", { centro: true }),
  salto(),
];

const indice = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun("Índice")] }),
  new TableOfContents("Índice", { hyperlink: true, headingStyleRange: "1-2" }),
  p([new TextRun({ text: "(Si el índice aparece vacío, en Word: clic derecho sobre él y \"Actualizar campos\".)", font: FUENTE, size: 18, italics: true, color: "808080" })]),
];

// ---------- cuerpo ----------
const cuerpo = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun("Resumen")] }),
  p("Se desarrolla una aplicación móvil para Android que reemplaza la bitácora en papel de los camioneros. Permite registrar los datos de cada viaje (cliente, ruta, carga, kilometraje, revisión del camión, paradas, descansos, cargas de combustible, peajes, novedades y la entrega) y entrega reportes que apoyan la toma de decisiones, como el gasto en combustible estimado frente al real y la ganancia de cada viaje."),
  p("La aplicación funciona sin conexión: en los tramos sin señal, todo se guarda en el teléfono y se envía automáticamente a la nube cuando vuelve la conexión, sin perder ni duplicar registros. La ubicación de cada evento se toma del GPS del teléfono, que funciona sin internet. Un administrador, desde la misma aplicación, revisa los viajes de todos los choferes, aprueba u observa las bitácoras y consulta reportes mensuales."),
  p("El sistema es la evolución de la aplicación \"Diario del Camionero\" desarrollada en un semestre anterior, que tenía las pantallas de inicio de sesión, registro y registro de viaje, pero no guardaba información. Esta versión conserva su base (Kotlin y Jetpack Compose) y agrega almacenamiento local, sincronización, seguridad, roles y una interfaz rediseñada."),

  h1("Descripción general de la empresa"),
  p([pendiente("[Por completar por el grupo: rubro y ubicación, organización (cantidad de personas y estructura), productos y servicios, y descripción breve del problema actual en el área operativa y de transporte. Indicar si la empresa es real o un caso de estudio.]")]),

  h1("Objetivos"),
  h2("Objetivo general"),
  p("Desarrollar una aplicación móvil nativa en Kotlin para el registro de bitácoras y la gestión operativa de camioneros, que funcione sin conexión y se sincronice con una base de datos PostgreSQL en la nube, para centralizar el control de viajes, tiempos, gastos y rutas."),
  h2("Objetivos específicos"),
  vineta("Analizar el proceso operativo actual de los conductores y el control de bitácoras en papel o planillas, para definir los requerimientos funcionales y de experiencia de usuario."),
  vineta("Diseñar la arquitectura del sistema, el modelo de base de datos relacional y las interfaces de la aplicación, optimizadas para el uso diario en ruta y sin señal."),
  vineta("Implementar y probar los módulos de la aplicación y su conexión con la base de datos en la nube, validando el registro de bitácoras, la sincronización sin pérdida ni duplicación de datos y la seguridad de acceso por rol."),

  h1("Descripción general del problema"),
  h2("Cómo se resuelve hoy"),
  p("El control y seguimiento de las operaciones de transporte de carga se gestiona de manera fragmentada y reactiva. Los conductores anotan sus viajes, tiempos de conducción y novedades durante la ruta con métodos tradicionales, lo que genera demoras en la comunicación con la central y dificulta la supervisión del estado de los camiones y despachos."),
  h2("Cómo se almacenan y procesan los datos"),
  p("La información se recopila en bitácoras de papel o en planillas Excel locales y desconectadas entre sí. El procesamiento depende de la transcripción manual del personal administrativo, un proceso lento y propenso a errores (datos ilegibles, pérdida de documentos o cifras duplicadas) que dificulta generar reportes consolidados o históricos."),
  h2("Flujo de datos"),
  p("Hoy el conductor registra los datos en papel y los entrega al volver a la base o los comunica por mensajería instantánea; luego el personal administrativo los traspasa a planillas para calcular costos y auditar horas de viaje. Con el sistema propuesto el flujo se digitaliza: el conductor ingresa los datos en la aplicación (con o sin señal), estos se guardan primero en el teléfono, se sincronizan con la base de datos central y la administración los consulta y valida desde la misma aplicación."),
  h2("Tipos de usuarios"),
  vineta([b("Chofer: "), t("registra sus viajes, la revisión del camión, paradas, descansos, gastos, novedades y la entrega, muchas veces sin señal.")]),
  vineta([b("Administrador: "), t("revisa los viajes de todos los choferes, aprueba u observa las bitácoras, administra los camiones y consulta reportes. Trabaja con conexión.")]),

  h1("Alcance del proyecto"),
  h2("Qué hará el sistema"),
  vineta([b("Autenticación y roles: "), t("inicio de sesión con correo y contraseña, registro y recuperación de contraseña con código. Cada usuario tiene rol de chofer o de administrador.")]),
  vineta([b("Registro de bitácora del chofer: "), t("datos del viaje, camión y kilometraje inicial, revisión del camión antes de salir, paradas y descansos con hora y ubicación GPS automáticas, cargas de combustible, peajes, novedades con gravedad, entrega con kilometraje final y foto o firma de quien recibe, y resumen del viaje.")]),
  vineta([b("Alerta de horas de manejo: "), t("aviso al chofer al acercarse y al superar 5 horas de manejo continuo; un descanso de al menos 30 minutos reinicia el conteo. Se calcula en el teléfono, por lo que funciona sin señal.")]),
  vineta([b("Modo sin conexión: "), t("todo lo del chofer se guarda primero en el teléfono y se sincroniza automáticamente al recuperar la conexión, sin perder ni duplicar datos.")]),
  vineta([b("Gestión administrativa: "), t("lista de viajes de todos los choferes actualizada en vivo, aprobación u observación de bitácoras, reportes mensuales por chofer y por camión, y administración de camiones. Se realiza en la misma aplicación, con el rol de administrador.")]),
  vineta([b("Despliegue: "), t("base de datos en la nube (Supabase) y entrega de la aplicación como APK instalable; publicación en la pista de prueba de Google Play si los plazos lo permiten.")]),
  h2("Qué no hará"),
  vineta([b("Telemetría por OBD-II: "), t("no se conectará al vehículo; el ingreso es manual o asistido por el teléfono.")]),
  vineta([b("Pagos o facturación: "), t("se limita al control operativo y de bitácoras.")]),
  vineta([b("Seguimiento GPS continuo: "), t("solo se registra la ubicación en cada evento (inicio, paradas, gastos, novedades, entrega).")]),
  vineta([b("Panel web: "), t("la administración se realiza desde la aplicación móvil.")]),
  vineta([b("Estimación automática de peajes: "), t("los peajes los ingresa el chofer; el combustible se estima con los kilómetros recorridos y el rendimiento del camión.")]),
  vineta([b("Versión para iOS y publicación en producción en Google Play: "), t("la publicación en producción exige 14 días de prueba cerrada con al menos 12 testers, lo que excede el plazo del semestre.")]),

  h1("Metodología"),
  h2("Desarrollo del proyecto"),
  p("Se adopta un enfoque híbrido. Al existir una base funcional de una etapa anterior, el alcance, las restricciones técnicas y los objetivos están delimitados desde el inicio, lo que permite planificar por hitos y entregables. Para la ejecución técnica se integran principios adaptativos que permiten gestionar riesgos, evaluar el código heredado y ajustar los tiempos según la complejidad encontrada."),
  h2("Diseño del producto"),
  p("Se utiliza Design Thinking para que las mejoras respondan a necesidades reales de uso:"),
  vineta([b("Empatizar y definir: "), t("identificar los momentos del viaje en que el chofer usa el teléfono (al salir, en ruta y al entregar) y qué registra en cada uno.")]),
  vineta([b("Idear: "), t("proponer flujos con pocos toques y botones grandes, pensados para usarse en la cabina.")]),
  vineta([b("Prototipar y evaluar: "), t("se elaboraron maquetas de dos direcciones visuales y se eligió \"Señalética de autopista\": fondo oscuro tipo asfalto, el viaje en curso presentado como un letrero verde de autopista y el ámbar reservado para la acción principal.")]),
  h2("Programación"),
  p("Se aplican prácticas de Extreme Programming (XP):"),
  vineta([b("Pruebas antes del código: "), t("cada regla de negocio (cálculos, validaciones, almacenamiento local y sincronización) se escribe primero como prueba automatizada.")]),
  vineta([b("Refactorización continua: "), t("el código heredado se reorganiza en una arquitectura por capas (MVVM) sin alterar el comportamiento esperado.")]),
  vineta([b("Integración continua: "), t("cambios pequeños, versionados con Git y compilados y probados en cada paso.")]),
  vineta([b("Estándares de codificación: "), t("nombres en español del dominio, tipado estricto y documentación interna.")]),

  h1("Cronograma de actividades"),
  p("Plazo de entrega: 25 de noviembre de 2026."),
  tabla([1900, 3700, 1500, 1926], [
    ["Fase", "Tareas principales", "Fechas", "Estado"],
    ["1. Análisis", "Requerimientos, alcance y revisión del informe", "4 al 10 nov", "Terminada"],
    ["2. Diseño", "Arquitectura, modelo de datos, maquetas y plan de implementación", "10 nov", "Terminada"],
    ["3. Desarrollo base", "Proyecto Android, base local, cálculos, sistema visual", "11 nov", "Terminada"],
    ["4. Flujo del chofer", "Nuevo viaje, revisión, hoja de ruta, registros, entrega y resumen", "11 nov", "Terminada (sin foto o firma)"],
    ["5. Nube y seguridad", "Base de datos Supabase, inicio de sesión, roles y sincronización", "12 al 16 nov", "Pendiente"],
    ["6. Administrador", "Viajes en vivo, aprobación, reportes y camiones", "17 al 20 nov", "Pendiente"],
    ["7. Pruebas y cierre", "Foto o firma, pruebas en teléfonos reales, ajustes, informe final", "21 al 25 nov", "Pendiente"],
  ]),

  h1("Recursos a utilizar"),
  h2("Software"),
  tabla([2200, 2600, 1400, 1700, 1126], [
    ["Componente", "Software", "Versión", "Licencia", "Costo"],
    ["Control de versiones", "Git", "2.x", "GPLv2", "$0"],
    ["Entorno de desarrollo", "Android Studio", "2026", "Gratuita", "$0"],
    ["Lenguaje e interfaz", "Kotlin + Jetpack Compose (Material 3)", "2.4 / BOM 2026.09", "Apache 2.0", "$0"],
    ["Base de datos local", "Room (SQLite)", "2.8", "Apache 2.0", "$0"],
    ["Base de datos en la nube", "Supabase (PostgreSQL, autenticación, API REST, almacenamiento de archivos)", "Plan gratuito", "Apache 2.0 / servicio", "$0"],
    ["Ubicación", "Google Play Services Location (GPS del teléfono)", "21.4", "Gratuita", "$0"],
    ["Distribución", "APK instalable / Google Play Console (opcional)", "-", "Pago único", "$0 a US$25 aprox."],
  ]),
  h2("Hardware"),
  tabla([2400, 3600, 2000, 1026], [
    ["Recurso", "Características", "Uso", "Costo"],
    ["Notebook de desarrollo", "Intel Core i7, 16 GB RAM, SSD", "Desarrollo y emulador Android", "$0 (existente)"],
    ["Teléfonos Android", "Android 8.0 o superior, con GPS", "Pruebas reales y demostración", "$0 (existentes)"],
    ["Servidor en la nube", "Supabase, plan gratuito", "Base de datos, autenticación y archivos", "$0"],
  ]),

  h1("Arquitectura del sistema"),
  p("La aplicación sigue el patrón MVVM (Modelo, Vista, Modelo de Vista). Las pantallas del chofer leen y escriben siempre en la base de datos del teléfono, nunca directamente en internet; un componente de sincronización se encarga de comunicar el teléfono con la nube cuando hay conexión."),
  tabla([2600, 6426], [
    ["Capa", "Responsabilidad"],
    ["Interfaz (Jetpack Compose)", "Pantallas: inicio, nuevo viaje, hoja de ruta, entrega, resumen, mis viajes, perfil y pantallas de administración."],
    ["Modelos de vista", "Mantienen el estado de cada pantalla y validan lo que ingresa el usuario."],
    ["Repositorios", "Reglas del negocio: crear viajes, registrar eventos, gastos y novedades, finalizar, impedir dos viajes en curso y bloquear viajes aprobados."],
    ["Base local (Room / SQLite)", "Fuente de verdad del chofer. Cada registro lleva una marca de \"pendiente de envío\"."],
    ["Sincronización (WorkManager)", "Envía los pendientes y descarga los cambios cuando hay conexión, con reintentos automáticos."],
    ["Nube (Supabase)", "PostgreSQL con seguridad por fila, autenticación, API REST generada automáticamente, almacenamiento de fotos y firmas, y actualizaciones en vivo para el administrador."],
  ]),

  h1("Modelo de datos"),
  p("La base de datos es relacional (PostgreSQL). Cada viaje pertenece a un chofer y usa un camión; de cada viaje dependen su revisión, eventos, gastos, novedades y adjuntos."),
  tabla([1800, 4626, 2600], [
    ["Tabla", "Campos principales", "Relación"],
    ["perfiles", "id, nombre completo, RUT, teléfono, rol (chofer o administrador), activo", "1 a 1 con la cuenta de usuario"],
    ["camiones", "id, patente, marca, modelo, año, rendimiento km/L, activo", "Un camión, muchos viajes"],
    ["viajes", "id, chofer, camión, cliente, origen, destino, ruta, tipo de carga, peso, refrigeración, precio, fechas de salida y llegada, km inicial y final, estado, validación, comentario de validación", "Pertenece a un chofer y a un camión"],
    ["revisiones", "id, viaje, neumáticos, luces, frenos, niveles, documentos, comentario", "1 a 1 con viaje"],
    ["eventos", "id, viaje, tipo (inicio, parada, descanso, carga, descarga, entrega, fin), hora de inicio y término, latitud, longitud, dirección, km, nota", "Muchos por viaje"],
    ["gastos", "id, viaje, tipo (combustible, peaje, alimentación, mantención, otro), monto, litros, hora, descripción", "Muchos por viaje"],
    ["novedades", "id, viaje, tipo, gravedad (baja, media, alta), descripción, hora, ubicación", "Muchos por viaje"],
    ["adjuntos", "id, viaje, tipo (foto o firma), archivo, nombre de quien recibe, hora", "Muchos por viaje"],
  ]),
  p("Además, una vista resume cada viaje (kilómetros, gasto en combustible y peajes, gasto total y margen) para los reportes del administrador."),

  h1("Funcionamiento sin conexión"),
  p("Es el núcleo del sistema, porque las rutas tienen tramos sin señal. Se aplican estas reglas:"),
  vineta([b("Identificadores creados en el teléfono: "), t("cada registro nace con un identificador único definitivo (UUID), sin esperar al servidor.")]),
  vineta([b("Cola de pendientes: "), t("cada registro guardado queda marcado como pendiente hasta que el servidor confirma que lo recibió. El envío es \"insertar o actualizar\" por identificador, por lo que reintentar nunca duplica.")]),
  vineta([b("Sin falsos envíos: "), t("si un registro se modifica mientras se está enviando, sigue pendiente para el próximo intento.")]),
  vineta([b("Orden de envío: "), t("primero los viajes y después sus registros dependientes, respetando las relaciones de la base de datos.")]),
  vineta([b("Indicador visible: "), t("el chofer ve en todo momento \"Sin señal. N registros se enviarán solos\", \"Enviando…\" o \"Todo enviado\".")]),
  vineta([b("GPS sin internet: "), t("la latitud y longitud se guardan siempre; la dirección legible se completa al sincronizar. Si el GPS no está disponible, el registro se guarda igual, sin bloquear al chofer.")]),

  h1("Seguridad"),
  vineta("Las contraseñas las administra el servicio de autenticación con cifrado (hash); la aplicación no las guarda. La versión anterior almacenaba la contraseña en el modelo de usuario."),
  vineta("Seguridad por fila en la base de datos: un chofer solo puede ver y modificar sus propios viajes; el administrador puede ver todos."),
  vineta("Un chofer no puede cambiar su rol ni aprobar sus propios viajes; una bitácora aprobada queda bloqueada para edición. Estas reglas se aplican en la base de datos, no solo en la aplicación."),
  vineta("La sesión se guarda cifrada en el teléfono, las comunicaciones son solo por HTTPS y los datos de la aplicación no se respaldan en la nube de Google."),
  vineta("Los permisos de ubicación y cámara se piden en el momento de usarlos, explicando para qué."),

  h1("Análisis de riesgos"),
  tabla([2500, 1050, 1000, 4476], [
    ["Riesgo", "Probabilidad", "Impacto", "Mitigación y contingencia"],
    ["Fallas en la sincronización sin conexión (pérdida o duplicación de datos)", "Media", "Alto", "Identificadores generados en el teléfono, envío idempotente y pruebas automatizadas de cortes de red. Contingencia: los registros permanecen en el teléfono y se puede forzar \"Sincronizar ahora\"."],
    ["Resistencia al cambio de los choferes", "Media", "Alto", "Interfaz con botones grandes y pocos toques por registro. Contingencia: capacitación breve y guía rápida de uso."],
    ["GPS sin señal o permiso denegado", "Media", "Medio", "El registro se guarda sin coordenadas y nunca bloquea al chofer."],
    ["Incompatibilidad con teléfonos antiguos", "Media", "Medio", "Versión mínima Android 8.0 (API 26) y pruebas en emulador y teléfonos reales."],
    ["Caída del servicio en la nube", "Baja", "Alto", "La aplicación del chofer sigue funcionando sin conexión y sincroniza al restablecerse el servicio."],
    ["Publicación en Google Play fuera de plazo", "Alta", "Bajo", "Se entrega APK instalable; la publicación en producción exige 14 días de prueba cerrada."],
    ["Plazo acotado (entrega 25 de noviembre)", "Media", "Alto", "Prioridad al funcionamiento sin conexión y al flujo del chofer; plan de implementación por tareas con avance diario."],
  ]),

  h1("Organización del grupo"),
  p([pendiente("[Por completar por el grupo: roles y responsabilidades de cada integrante, estructura de toma de decisiones y distribución de tareas.]")]),

  h1("Consecuencias del sistema de información"),
  p("La implementación del sistema transformará el modelo operativo actual, reemplazando procesos manuales fragmentados por una solución digital centralizada."),
  h2("Beneficios directos"),
  vineta([b("Eliminación del papel: "), t("se reduce el uso de bitácoras físicas y planillas desconectadas, evitando la transcripción manual y la pérdida de documentos.")]),
  vineta([b("Información oportuna: "), t("la administración ve los viajes, tiempos y novedades en cuanto el teléfono tiene conexión.")]),
  vineta([b("Continuidad sin señal: "), t("los choferes registran en zonas sin cobertura sin riesgo de perder información.")]),
  vineta([b("Control de costos: "), t("ganancia por viaje y comparación entre combustible estimado y cargado.")]),
  vineta([b("Seguridad vial: "), t("alerta de horas de manejo continuo.")]),
  h2("Beneficios indirectos"),
  vineta([b("Menos errores: "), t("validaciones al ingresar datos y cálculos automáticos.")]),
  vineta([b("Mejor experiencia del conductor: "), t("registro rápido desde el teléfono en lugar de llenar papeles.")]),
  vineta([b("Trazabilidad: "), t("historial centralizado que facilita auditorías y control de jornadas.")]),
  h2("Impacto en la operación"),
  p("Al automatizar el flujo de datos desde el teléfono hasta la base central, el personal administrativo deja de digitar bitácoras y puede dedicarse a la supervisión y planificación. La administración obtiene visibilidad sobre tramos, tiempos, gastos y novedades de cada vehículo, y la empresa se presenta ante sus clientes como un prestador de transporte más confiable y transparente."),

  h1("Estado de avance al 11 de noviembre"),
  p("Se construyó el flujo completo del chofer funcionando sin conexión. Las capturas fueron tomadas en el emulador con el teléfono en modo avión."),
  h2("Terminado"),
  vineta("Proyecto Android con arquitectura MVVM, base de datos local y sistema visual \"Señalética de autopista\"."),
  vineta("Crear viaje en tres pasos (datos del viaje, camión y kilometraje, revisión del camión), impidiendo tener dos viajes en curso."),
  vineta("Inicio con el viaje en curso, indicador de sincronización y resumen del mes."),
  vineta("Hoja de ruta: línea de tiempo con hitos kilométricos que mezcla eventos, gastos y novedades."),
  vineta("Registro de paradas, descansos, carga y descarga con GPS; combustible, peajes y otros gastos; novedades con gravedad; alerta de horas de manejo."),
  vineta("Entrega con kilometraje final y resumen del viaje con ganancia y combustible estimado frente al real."),
  vineta("39 pruebas automatizadas en verde (cálculos, validaciones, base local y reglas del repositorio)."),
  h2("Pendiente"),
  vineta("Base de datos en Supabase, inicio de sesión, roles y sincronización con la nube."),
  vineta("Pantallas del administrador: viajes en vivo, aprobación de bitácoras, reportes y camiones."),
  vineta("Foto o firma de quien recibe la carga."),
  vineta("Pruebas en teléfonos reales y ajustes finales."),
  h2("Capturas"),
  filaCapturas([["04-inicio-viaje-en-curso.png", "Inicio con viaje en curso, sin señal"], ["02-nuevo-viaje-camion.png", "Nuevo viaje: camión"], ["03-revision-camion.png", "Revisión del camión"]]),
  filaCapturas([["05-registrar-combustible.png", "Registro de combustible"], ["06-hoja-de-ruta.png", "Hoja de ruta con hitos"], ["07-resumen-viaje.png", "Resumen del viaje"]]),
];

const doc = new Document({
  creator: "Grupo Taller de Sistemas de Información",
  title: "Sistema Móvil de Gestión de Bitácora para Camioneros",
  styles: {
    default: { document: { run: { font: FUENTE, size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FUENTE, size: 30, bold: true, color: VERDE },
        paragraph: { spacing: { before: 360, after: 160 }, keepNext: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FUENTE, size: 24, bold: true, color: "262626" },
        paragraph: { spacing: { before: 240, after: 100 }, keepNext: true, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [{
      reference: "vinetas",
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
        { level: 1, format: LevelFormat.BULLET, text: "◦", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1440, hanging: 360 } } } },
      ],
    }],
  },
  features: { updateFields: true },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    footers: {
      default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [
        new TextRun({ children: [PageNumber.CURRENT], font: FUENTE, size: 18, color: "808080" })] })] }),
    },
    children: [...portada, ...indice, ...cuerpo],
  }],
});

Packer.toBuffer(doc).then(buf => { fs.writeFileSync(SALIDA, buf); console.log("ok", SALIDA, buf.length); });
