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
  p([new TextRun({ text: "Versión 1.2, con el avance del prototipo al 11 de noviembre de 2026", font: FUENTE, size: 22, italics: true })], { centro: true }),
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
];

// ---------- cuerpo ----------
const cuerpo = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun("Resumen")] }),
  p("Proponemos una aplicación para Android que reemplace la bitácora en papel de los camioneros. En ella el chofer registra cada viaje: cliente, ruta, carga, kilometraje, la revisión del camión antes de salir, paradas, descansos, cargas de combustible, peajes, novedades y la entrega. Con esos datos la aplicación calcula, por ejemplo, la ganancia de cada viaje y compara el combustible que se cargó con el que se debió gastar según los kilómetros recorridos."),
  p("Como en ruta hay tramos sin señal, la aplicación guarda todo en el teléfono y lo envía a la nube cuando vuelve la conexión. La ubicación de cada registro sale del GPS del teléfono, que funciona sin internet. El administrador usa la misma aplicación para revisar los viajes de los choferes, aprobar u observar sus bitácoras y ver reportes mensuales."),
  p("El proyecto parte de \"Diario del Camionero\", una aplicación que hicimos en un semestre anterior. Esa versión tenía inicio de sesión, registro de usuario y un formulario de viaje, pero no guardaba nada. Mantenemos su base en Kotlin y Jetpack Compose y le agregamos almacenamiento local, sincronización, seguridad, roles y una interfaz nueva."),

  h1("Descripción general de la empresa"),
  p([pendiente("[Por completar por el grupo: rubro y ubicación, organización (cantidad de personas y estructura), productos y servicios, y descripción breve del problema actual en el área operativa y de transporte. Indicar si la empresa es real o un caso de estudio.]")]),

  h1("Objetivos"),
  h2("Objetivo general"),
  p("Desarrollar una aplicación móvil nativa en Kotlin para que los camioneros registren su bitácora y la empresa controle sus viajes, tiempos, gastos y rutas. La aplicación debe funcionar sin conexión y sincronizarse con una base de datos PostgreSQL en la nube."),
  h2("Objetivos específicos"),
  vineta("Analizar cómo trabajan hoy los conductores y cómo se controlan las bitácoras en papel o en planillas, para definir los requerimientos de la aplicación."),
  vineta("Diseñar la arquitectura, el modelo de base de datos relacional y las pantallas, pensando en que se usan en ruta y muchas veces sin señal."),
  vineta("Implementar y probar la aplicación y su conexión con la base de datos, verificando que los registros se sincronicen sin perderse ni duplicarse y que cada usuario acceda solo a lo que le corresponde según su rol."),

  h1("Descripción general del problema"),
  h2("Cómo se resuelve hoy"),
  p("Hoy los conductores anotan sus viajes, horas de manejo y novedades en papel durante la ruta. La central se entera tarde de lo que pasa y no puede seguir el estado de los camiones y despachos mientras ocurren."),
  h2("Cómo se almacenan y procesan los datos"),
  p("Las anotaciones quedan en bitácoras de papel o en planillas Excel que no están conectadas entre sí. El personal administrativo las transcribe a mano, lo que toma tiempo y produce errores: datos ilegibles, documentos perdidos o cifras duplicadas. Con la información así repartida es difícil armar reportes o revisar el historial."),
  h2("Flujo de datos"),
  p("El conductor anota en papel y entrega la bitácora al volver a la base, o envía los datos por mensajería. Después, alguien de la oficina los pasa a una planilla para calcular costos y revisar las horas de viaje. Con el sistema propuesto, el conductor ingresa los datos en la aplicación, con o sin señal; estos quedan en el teléfono, se sincronizan con la base de datos central y la administración los revisa desde la misma aplicación."),
  h2("Tipos de usuarios"),
  p("El chofer registra sus viajes, la revisión del camión, paradas, descansos, gastos, novedades y la entrega, muchas veces sin señal. El administrador revisa los viajes de todos los choferes, aprueba u observa las bitácoras, mantiene la lista de camiones y consulta los reportes; trabaja desde la oficina, con conexión."),

  h1("Alcance del proyecto"),
  h2("Qué hará el sistema"),
  p("Cada usuario entra con su correo y contraseña. También podrá registrarse y recuperar su contraseña con un código que llega al correo. Hay dos roles, chofer y administrador, y la aplicación muestra un menú distinto a cada uno."),
  p("El chofer crea el viaje antes de salir: datos del viaje, camión, kilometraje inicial y una revisión rápida del camión. En ruta registra paradas y descansos (la hora y la ubicación se toman solas), cargas de combustible, peajes y novedades con su gravedad. Al llegar anota el kilometraje final y la entrega, con una foto o la firma de quien recibe, y ve un resumen del viaje."),
  p("La aplicación avisa al chofer cuando se acerca a 5 horas de manejo continuo y cuando las supera. Un descanso de 30 minutos o más reinicia el conteo. El cálculo se hace en el teléfono, así que el aviso funciona aunque no haya señal."),
  p("Todo lo que registra el chofer se guarda primero en el teléfono y se envía solo cuando hay conexión. El administrador ve la lista de viajes de todos los choferes, que se actualiza sola cuando llegan datos nuevos, aprueba u observa cada bitácora, revisa reportes mensuales por chofer y por camión, y mantiene la lista de camiones."),
  p("La base de datos queda en Supabase, en la nube. La aplicación se entrega como APK para instalar en el teléfono; si alcanza el tiempo, también se publica en la pista de prueba de Google Play."),
  h2("Qué no hará"),
  vineta("No se conectará al camión por OBD-II. Los datos los ingresa el chofer, con ayuda del GPS del teléfono."),
  vineta("No incluye pagos ni facturación."),
  vineta("No hace seguimiento GPS continuo. La ubicación se guarda solo al registrar algo: inicio, paradas, gastos, novedades y entrega."),
  vineta("No tendrá panel web. El administrador trabaja desde la aplicación móvil."),
  vineta("No estima peajes automáticamente; el chofer los ingresa. El combustible se estima con los kilómetros recorridos y el rendimiento del camión."),
  vineta("No tendrá versión para iOS ni se publicará en producción en Google Play, porque eso exige 14 días de prueba cerrada con al menos 12 personas y no alcanza en el semestre."),

  h1("Metodología"),
  h2("Desarrollo del proyecto"),
  p("Usamos un enfoque híbrido. Como ya existía una versión anterior de la aplicación, el alcance y las restricciones técnicas estaban claros desde el principio, y eso permite planificar por hitos y entregables. Durante el desarrollo ajustamos los tiempos según lo que fuimos encontrando en el código anterior y en los riesgos del proyecto."),
  h2("Diseño del producto"),
  p("Para el diseño seguimos Design Thinking. Primero identificamos en qué momentos del viaje el chofer usa el teléfono (al salir, en ruta y al entregar) y qué necesita anotar en cada uno. Con eso propusimos flujos con pocos toques y botones grandes, porque la aplicación se usa en la cabina. Luego hicimos maquetas de dos estilos visuales y elegimos uno inspirado en la señalética de las autopistas: fondo oscuro, el viaje en curso dibujado como un letrero verde de autopista y el color ámbar solo para la acción principal de cada pantalla."),
  h2("Programación"),
  p("Para programar aplicamos prácticas de Extreme Programming. Cada regla de negocio (cálculos, validaciones, base de datos local y sincronización) se escribe primero como prueba automatizada y después se programa. El código de la versión anterior se reorganizó en capas siguiendo el patrón MVVM. Trabajamos con cambios pequeños, versionados en Git, que se compilan y prueban en cada paso, y usamos nombres en español del dominio del negocio."),

  h1("Cronograma de actividades"),
  p("La entrega es el 25 de noviembre de 2026."),
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
    ["Lenguaje e interfaz", "Kotlin y Jetpack Compose (Material 3)", "2.4 / BOM 2026.09", "Apache 2.0", "$0"],
    ["Base de datos local", "Room (SQLite)", "2.8", "Apache 2.0", "$0"],
    ["Base de datos en la nube", "Supabase (PostgreSQL, autenticación, API REST y almacenamiento de archivos)", "Plan gratuito", "Apache 2.0 / servicio", "$0"],
    ["Ubicación", "Google Play Services Location (GPS del teléfono)", "21.4", "Gratuita", "$0"],
    ["Distribución", "APK instalable / Google Play Console (opcional)", "-", "Pago único", "$0 a US$25 aprox."],
  ]),
  h2("Hardware"),
  tabla([2400, 3600, 2000, 1026], [
    ["Recurso", "Características", "Uso", "Costo"],
    ["Notebook de desarrollo", "Intel Core i7, 16 GB RAM, SSD", "Desarrollo y emulador Android", "$0 (existente)"],
    ["Teléfonos Android", "Android 8.0 o superior, con GPS", "Pruebas y demostración", "$0 (existentes)"],
    ["Servidor en la nube", "Supabase, plan gratuito", "Base de datos, autenticación y archivos", "$0"],
  ]),

  h1("Arquitectura del sistema"),
  p("La aplicación sigue el patrón MVVM (Modelo, Vista, Modelo de Vista). Las pantallas del chofer leen y escriben siempre en la base de datos del teléfono, nunca directo en internet. Un proceso aparte se encarga de enviar y recibir datos de la nube cuando hay conexión."),
  tabla([2600, 6426], [
    ["Capa", "Qué hace"],
    ["Interfaz (Jetpack Compose)", "Pantallas de inicio, nuevo viaje, hoja de ruta, entrega, resumen, mis viajes y perfil, más las del administrador."],
    ["Modelos de vista", "Guardan el estado de cada pantalla y validan lo que escribe el usuario."],
    ["Repositorios", "Aplican las reglas del negocio: crear viajes, registrar eventos, gastos y novedades, cerrar el viaje, impedir dos viajes en curso y bloquear los viajes aprobados."],
    ["Base local (Room / SQLite)", "Es la fuente de datos del chofer. Cada registro tiene una marca que indica si falta enviarlo."],
    ["Sincronización (WorkManager)", "Envía lo pendiente y descarga los cambios cuando hay conexión. Si falla, reintenta más tarde."],
    ["Nube (Supabase)", "PostgreSQL con seguridad por fila, autenticación, API REST generada automáticamente, almacenamiento de fotos y firmas, y avisos en vivo para la lista del administrador."],
  ]),

  h1("Modelo de datos"),
  p("La base de datos es relacional (PostgreSQL). Cada viaje pertenece a un chofer y usa un camión. Cada viaje tiene una revisión y puede tener varios eventos, gastos, novedades y adjuntos."),
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
  p("Para los reportes del administrador hay además una vista que resume cada viaje: kilómetros, gasto en combustible y peajes, gasto total y margen."),

  h1("Funcionamiento sin conexión"),
  p("Las rutas tienen tramos sin señal, por eso la aplicación está pensada para trabajar sin internet desde el principio."),
  p("Cada registro recibe en el teléfono un identificador único (UUID) que ya es el definitivo, así no hay que esperar al servidor para crearlo. El registro queda marcado como pendiente hasta que el servidor confirma que lo recibió. El envío inserta el registro o lo actualiza si ya existía con ese identificador, de modo que reintentar no crea duplicados. Si el chofer modifica un registro mientras se está enviando, este sigue marcado como pendiente y se vuelve a enviar en el siguiente intento."),
  p("Los viajes se envían antes que sus registros dependientes, para respetar las relaciones de la base de datos. En la pantalla de inicio el chofer siempre ve el estado: \"Sin señal. N registros se enviarán solos\", \"Enviando…\" o \"Todo enviado\"."),
  p("El GPS funciona sin internet, así que la latitud y la longitud se guardan siempre; la dirección en texto se completa al sincronizar. Si el GPS no responde o el chofer no dio permiso de ubicación, el registro se guarda igual, sin coordenadas."),

  h1("Seguridad"),
  vineta("Las contraseñas las maneja el servicio de autenticación de Supabase, que las guarda cifradas (hash). La aplicación no las almacena; la versión anterior sí guardaba la contraseña en el modelo de usuario."),
  vineta("La base de datos tiene seguridad por fila: un chofer solo puede ver y modificar sus propios viajes, y el administrador puede ver todos."),
  vineta("Un chofer no puede cambiar su propio rol ni aprobar sus viajes, y una bitácora aprobada ya no se puede editar. Estas reglas están en la base de datos, no solo en la aplicación."),
  vineta("La sesión se guarda cifrada en el teléfono, la comunicación con el servidor es solo por HTTPS y los datos de la aplicación no se respaldan en la nube de Google."),
  vineta("Los permisos de ubicación y cámara se piden recién cuando se van a usar, explicando para qué."),

  h1("Análisis de riesgos"),
  tabla([2500, 1050, 1000, 4476], [
    ["Riesgo", "Probabilidad", "Impacto", "Mitigación y contingencia"],
    ["Fallas en la sincronización sin conexión (pérdida o duplicación de datos)", "Media", "Alto", "Identificadores generados en el teléfono, envíos que se pueden repetir sin duplicar y pruebas automatizadas de cortes de red. Si algo falla, los registros siguen en el teléfono y se puede pedir \"Sincronizar ahora\"."],
    ["Resistencia al cambio de los choferes", "Media", "Alto", "Botones grandes y pocos toques por registro. Si cuesta la adopción, una capacitación corta y una guía rápida de uso."],
    ["GPS sin señal o permiso denegado", "Media", "Medio", "El registro se guarda sin coordenadas y no bloquea al chofer."],
    ["Teléfonos antiguos", "Media", "Medio", "Versión mínima Android 8.0 (API 26) y pruebas en emulador y en teléfonos reales."],
    ["Caída del servicio en la nube", "Baja", "Alto", "La aplicación del chofer sigue funcionando sin conexión y sincroniza cuando el servicio vuelve."],
    ["Publicación en Google Play fuera de plazo", "Alta", "Bajo", "Se entrega el APK instalable; la publicación en producción exige 14 días de prueba cerrada."],
    ["Plazo corto (entrega el 25 de noviembre)", "Media", "Alto", "Primero el funcionamiento sin conexión y el flujo del chofer; plan de implementación dividido en tareas con avance diario."],
  ]),

  h1("Organización del grupo"),
  p([pendiente("[Por completar por el grupo: roles y responsabilidades de cada integrante, estructura de toma de decisiones y distribución de tareas.]")]),

  h1("Consecuencias del sistema de información"),
  p("Con el sistema, la bitácora en papel y las planillas sueltas se reemplazan por registros digitales que llegan a una base de datos central."),
  h2("Beneficios directos"),
  p("Se deja de usar la bitácora de papel y nadie tiene que transcribirla, así que no se pierden documentos. La administración ve los viajes, tiempos y novedades apenas el teléfono del chofer tiene conexión, y el chofer puede registrar en zonas sin cobertura sin perder lo que anota. Además, cada viaje muestra su ganancia y compara el combustible estimado con el cargado, y el aviso de horas de manejo ayuda a que el chofer descanse a tiempo."),
  h2("Beneficios indirectos"),
  p("Hay menos errores, porque la aplicación valida los datos al ingresarlos y hace los cálculos sola. Para el conductor es más rápido registrar desde el teléfono que llenar papeles. Y el historial queda en un solo lugar, lo que facilita las auditorías y el control de jornadas."),
  h2("Impacto en la operación"),
  p("El personal administrativo deja de digitar bitácoras y puede dedicar ese tiempo a supervisar y planificar. La administración sabe qué tramos, tiempos, gastos y novedades tuvo cada camión, y con esa información puede responder mejor a sus clientes cuando preguntan por un despacho."),

  h1("Estado de avance al 11 de noviembre"),
  p("Ya funciona el flujo completo del chofer sin conexión. Las capturas de esta sección se tomaron en el emulador con el teléfono en modo avión."),
  h2("Terminado"),
  vineta("Proyecto Android con arquitectura MVVM, base de datos local y el estilo visual elegido."),
  vineta("Creación de viaje en tres pasos: datos del viaje, camión y kilometraje, y revisión del camión. La aplicación no deja tener dos viajes en curso."),
  vineta("Pantalla de inicio con el viaje en curso, el estado de sincronización y el resumen del mes."),
  vineta("Hoja de ruta: una línea de tiempo con hitos kilométricos que junta eventos, gastos y novedades."),
  vineta("Registro de paradas, descansos, carga y descarga con GPS; combustible, peajes y otros gastos; novedades con gravedad; y aviso de horas de manejo."),
  vineta("Entrega con kilometraje final y resumen del viaje con la ganancia y el combustible estimado frente al cargado."),
  vineta("39 pruebas automatizadas que pasan, sobre los cálculos, las validaciones, la base local y las reglas de los repositorios."),
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
