// Informe Final Proyecto SI. Uso: node generar-informe-final.js (necesita el paquete docx)
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, ImageRun, PageBreak, LevelFormat, TableOfContents, Footer,
  PageNumber, VerticalAlign,
} = require("docx");

const RAIZ = path.resolve(__dirname, "../..");
const SALIDA = path.join(RAIZ, "2026-2-TSI-IFSI-GRPx-INF.docx");
const FUENTE = "Arial";
const ANCHO = 9026; // A4 con márgenes de 2,54 cm
const VERDE = "0E6B4C";
const T = 24; // Arial 12
const T_TABLA = 19;

// ---------- utilidades ----------
const run = (x, o = {}) => new TextRun({ text: x, font: FUENTE, size: T, ...o });
const b = (x) => run(x, { bold: true });
const i = (x) => run(x, { italics: true });
const pend = (x) => run(x, { highlight: "yellow" });
const p = (texto, o = {}) => new Paragraph({
  spacing: { after: 160, line: 360 },
  alignment: o.centro ? AlignmentType.CENTER : AlignmentType.JUSTIFIED,
  keepNext: o.keepNext,
  children: (Array.isArray(texto) ? texto : [texto]).map(x => typeof x === "string" ? run(x, o.run) : x),
});
const h1 = (x, salto = false) => new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: salto, children: [new TextRun(x)] });
const h1s = (x) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(x)] });
const h2 = (x) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(x)] });
const h3 = (x) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(x)] });
const v = (partes, nivel = 0) => new Paragraph({
  numbering: { reference: "vinetas", level: nivel }, alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 360 },
  children: (Array.isArray(partes) ? partes : [partes]).map(x => typeof x === "string" ? run(x) : x),
});
const n = (partes, instancia = 1) => new Paragraph({
  numbering: { reference: "numeros", level: 0, instance: instancia }, alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 360 },
  children: (Array.isArray(partes) ? partes : [partes]).map(x => typeof x === "string" ? run(x) : x),
});
const espacio = (antes) => new Paragraph({ spacing: { before: antes }, children: [] });

const borde = { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" };
const bordes = { top: borde, bottom: borde, left: borde, right: borde };
const nada = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const sinBordes = { top: nada, bottom: nada, left: nada, right: nada };

function tabla(anchos, filas) {
  const total = anchos.reduce((a, c) => a + c, 0);
  return new Table({
    width: { size: total, type: WidthType.DXA }, columnWidths: anchos,
    rows: filas.map((fila, r) => new TableRow({
      tableHeader: r === 0, cantSplit: true,
      children: fila.map((celda, j) => new TableCell({
        width: { size: anchos[j], type: WidthType.DXA }, borders: bordes,
        shading: r === 0 ? { fill: VERDE, type: ShadingType.CLEAR, color: "auto" } : (r % 2 === 0 ? { fill: "F4F6F5", type: ShadingType.CLEAR, color: "auto" } : undefined),
        margins: { top: 60, bottom: 60, left: 100, right: 100 }, verticalAlign: VerticalAlign.CENTER,
        children: String(celda).split("\n").map(linea => new Paragraph({
          spacing: { after: 0, line: 260 },
          children: [new TextRun({ text: linea, font: FUENTE, size: T_TABLA, bold: r === 0, color: r === 0 ? "FFFFFF" : undefined })],
        })),
      })),
    })),
  });
}
const pieTabla = (x) => new Paragraph({ spacing: { before: 60, after: 240 }, alignment: AlignmentType.CENTER,
  children: [new TextRun({ text: x, font: FUENTE, size: 20, italics: true })] });

function figura(archivo, anchoPx, pie, carpeta = "docs/informe/figuras") {
  const ruta = path.join(RAIZ, carpeta, archivo);
  const buf = fs.readFileSync(ruta);
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 120 },
      children: [new ImageRun({ type: "png", data: buf, transformation: { width: anchoPx, height: Math.round(anchoPx * h / w) },
        altText: { title: pie, description: pie, name: archivo } })] }),
    pieTabla(pie),
  ];
}
function capturas(items) {
  const w = Math.floor(ANCHO / items.length);
  return new Table({
    width: { size: w * items.length, type: WidthType.DXA }, columnWidths: items.map(() => w),
    rows: [new TableRow({ cantSplit: true, children: items.map(([archivo, pie]) => new TableCell({
      width: { size: w, type: WidthType.DXA }, borders: sinBordes,
      children: [
        new Paragraph({ alignment: AlignmentType.CENTER, children: [new ImageRun({ type: "png",
          data: fs.readFileSync(path.join(RAIZ, "docs/capturas", archivo)), transformation: { width: 150, height: 333 },
          altText: { title: pie, description: pie, name: archivo } })] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 60, after: 200 },
          children: [new TextRun({ text: pie, font: FUENTE, size: 18, italics: true })] }),
      ],
    })) })],
  });
}

// ---------- 1. Portada ----------
const portada = [
  p([run("UNIVERSIDAD TECNOLÓGICA METROPOLITANA", { bold: true, size: 26 })], { centro: true }),
  p("Escuela de Informática", { centro: true }),
  p("Taller de Sistemas de Información", { centro: true }),
  p("2do Semestre - 2026", { centro: true }),
  espacio(500),
  p([run("Informe Final Proyecto SI", { bold: true, size: 30 })], { centro: true }),
  p([run("Bitácora del Camionero", { bold: true, size: 40, color: VERDE })], { centro: true }),
  p([run("Aplicación móvil para el registro de viajes y el control de operaciones de Transportes MA SpA", { size: 26 })], { centro: true }),
  espacio(500),
  p([b("Asignatura: "), run("Taller de Sistemas de Información")], { centro: true }),
  p([b("Profesora: "), run("Cindy Betzabe Contador Cisterna")], { centro: true }),
  p([b("Grupo: "), pend("N.º [por completar]")], { centro: true }),
  p([b("Integrantes:")], { centro: true }),
  ...["Ignacio Moreno Aguirre", "Nicolás Sepúlveda Catalán", "Francisco Jeria Guerra", "Benjamín Hernández", "Claudio Pizarro Cerda"]
    .map(x => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [run(x)] })),
  espacio(200),
  p([b("Fecha de presentación: "), run("2 de diciembre de 2026")], { centro: true }),
  p("Santiago, Chile", { centro: true }),
];

// ---------- 2. Índice ----------
const indice = [
  new Paragraph({ pageBreakBefore: true, spacing: { after: 200 }, children: [new TextRun({ text: "Índice", font: FUENTE, size: 32, bold: true, color: VERDE })] }),
  new TableOfContents("Índice", { hyperlink: true, headingStyleRange: "1-2" }),
];

// ---------- cuerpo ----------
const cuerpo = [
  // 3
  h1("3. Introducción", true),
  p("Transportes MA SpA es una empresa familiar de transporte de carga por carretera. Tiene un solo camión, un Volvo FH con rampla, y lo maneja su dueño, Hernán Moreno. Según la temporada y los fletes que aparezcan, los viajes van hacia Chiloé o hacia Antofagasta, que son trayectos de más de mil kilómetros con tramos largos sin señal de celular."),
  p("Hoy toda la información de cada viaje se lleva a mano: el kilometraje, las horas, las novedades y, sobre todo, las boletas de combustible, peajes y comida, que el dueño guarda una por una para saber al final cuánto le dejó el flete. Este informe describe el sistema que construimos para reemplazar ese cuaderno: una aplicación Android, llamada Bitácora del Camionero, que funciona sin señal y envía los datos a una base de datos en la nube cuando vuelve la conexión."),
  p("El proyecto parte de \"Diario del Camionero\", una aplicación que uno de los integrantes hizo en una asignatura anterior. Esa versión tenía un formulario de viaje, pero no guardaba los datos ni tenía usuarios reales. La reconstruimos completa: base de datos local, sincronización, inicio de sesión con roles, seguridad en el servidor, reportes y una interfaz nueva pensada para usarse en la cabina."),
  p("El informe sigue el esquema pedido por la asignatura. Las secciones 4 a 8 describen la empresa, el problema y los requerimientos. Las secciones 9 y 10 explican cómo se organizó el grupo y cómo avanzó el proyecto por fases. Las secciones 11 y 12 muestran el diseño y el sistema terminado, y las secciones 13 a 16 tratan los riesgos, las crisis, el plan de trabajo y la continuidad. Al final están las conclusiones y los anexos."),

  // 4
  h1("4. Descripción general de la empresa"),
  h2("4.1 Rubro y ubicación"),
  p("Transportes MA SpA se dedica al transporte de carga por carretera. Trabaja con fletes que consigue directamente y los viajes principales son hacia el sur, a Chiloé, y hacia el norte, a Antofagasta. Cuál de los dos domina depende de la temporada y de los fletes que salgan."),
  p([pend("[Por confirmar con el cliente: comuna donde está la base de la empresa y año de inicio de actividades.]")]),
  h2("4.2 Organización interna"),
  p("Es una empresa de una persona. Hernán Moreno es el dueño, el chofer y quien lleva la administración: busca los fletes, conversa el precio con el cliente, maneja el camión, paga los gastos en ruta y, al volver, ordena las boletas para sacar las cuentas del viaje. No hay oficina ni personal administrativo."),
  ...figura("01-organigrama.png", 560, "Figura 1. Organigrama simplificado de Transportes MA SpA."),
  h2("4.3 Productos y servicios"),
  p("El servicio es el traslado de carga en un camión con rampla hacia el sur o el norte del país. La empresa cobra un precio por flete, acordado de antemano con cada cliente según el origen, el destino, el tipo de carga y el peso."),
  h2("4.4 Breve descripción del problema"),
  p("Todo el control del negocio está en papel. Si una boleta se pierde o se moja, ese gasto desaparece de las cuentas. Saber cuánto dejó un viaje exige sumar a mano decenas de boletas, y comparar un viaje con otro, o un mes con otro, es casi imposible. Además, en viajes de más de mil kilómetros es fácil perder la cuenta de las horas de manejo continuo, que por seguridad no deberían pasar de cinco."),

  // 5
  h1("5. Objetivos"),
  h2("5.1 Objetivo general"),
  p("Desarrollar una aplicación móvil Android para que Transportes MA SpA registre la bitácora de cada viaje, sus gastos y la entrega de la carga, aunque no haya señal, y pueda revisar después la ganancia de cada viaje y los totales de cada mes desde una base de datos central."),
  h2("5.2 Objetivos específicos"),
  n("Levantar con el cliente cómo se registra hoy un viaje y qué datos necesita para controlar sus costos, y traducirlo en requerimientos funcionales y no funcionales."),
  n("Diseñar la arquitectura, el modelo de datos relacional y las pantallas, considerando que el teléfono se usa en la cabina y muchas veces sin cobertura."),
  n("Implementar la aplicación con almacenamiento local, sincronización con la nube, inicio de sesión y permisos por rol, de modo que ningún registro se pierda ni se duplique."),
  n("Validar el sistema con pruebas automatizadas y con recorridos completos en modo avión, y entregar al cliente un instalador listo para usar."),

  // 6
  h1("6. Descripción general del problema"),
  p([pend("[Validar con Hernán Moreno el flujo de esta sección y de la Figura 2 antes de la entrega.]")]),
  h2("6.1 Cómo se resuelve hoy"),
  p("Cuando aparece un flete, Hernán acuerda el precio por teléfono o WhatsApp, revisa el camión y sale. Durante el viaje anota en un cuaderno el kilometraje, las horas y lo que pasa en la ruta. Cada vez que carga combustible, paga un peaje o come, guarda la boleta. Al entregar, el cliente firma la guía de despacho en papel."),
  h2("6.2 Cómo se almacenan y procesan los datos"),
  p("Los datos quedan repartidos entre el cuaderno, las boletas sueltas y las guías firmadas. Al volver, Hernán los ordena y hace las cuentas a mano para saber cuánto gastó y cuánto le quedó del flete. No hay respaldo: si se pierde el cuaderno o una boleta, se pierde esa información."),
  h2("6.3 Flujo de datos actual"),
  p("El flujo actual se muestra en la Figura 2. Los datos se usan para tres cosas: calcular la ganancia de cada viaje, tener comprobantes de los gastos para la contabilidad y saber cuándo toca mantención o cambio de neumáticos. En los pasos marcados en ámbar la información existe solo en papel."),
  ...figura("02-flujo-actual.png", 600, "Figura 2. Flujo actual del proceso de un viaje."),
  h2("6.4 Tipos de usuarios"),
  p("Hoy existe un solo usuario, que cumple los dos papeles. El sistema separa esos papeles en dos roles, porque así lo necesita cualquier empresa de transporte que crezca y porque permite que otra persona de confianza revise las cuentas:"),
  tabla([2000, 3600, 3426], [
    ["Rol", "Qué hace en el sistema", "Dónde y cómo trabaja"],
    ["Chofer", "Crea el viaje, hace la revisión del camión, registra paradas, descansos, gastos y novedades, y cierra el viaje con la entrega.", "En la cabina, muchas veces sin señal, con el teléfono en un soporte."],
    ["Administrador", "Revisa todos los viajes, aprueba u observa cada bitácora, ve reportes mensuales y mantiene la lista de camiones.", "En la casa u oficina, con conexión. Hoy es el mismo Hernán; mañana puede ser un contador o un segundo socio."],
  ]),
  pieTabla("Tabla 1. Usuarios del sistema."),

  // 7
  h1("7. Toma de requerimientos"),
  p("Los requerimientos salieron de conversaciones con el cliente sobre cómo trabaja hoy, de la revisión de la aplicación anterior y de la propuesta del grupo (informes 1.1 y 1.2). Se ordenaron según el momento del viaje en que se usan: al salir, en ruta y al llegar."),
  h2("7.1 Requerimientos funcionales"),
  tabla([900, 6326, 1800], [
    ["ID", "Requerimiento", "Rol"],
    ["RF01", "Crear una cuenta de chofer con nombre, correo y contraseña.", "Chofer"],
    ["RF02", "Iniciar sesión con correo y contraseña y seguir entrando aunque no haya señal.", "Ambos"],
    ["RF03", "Recuperar la contraseña mediante un enlace enviado al correo.", "Ambos"],
    ["RF04", "Crear un viaje en tres pasos: datos del flete, camión con kilometraje inicial y revisión del camión.", "Chofer"],
    ["RF05", "Impedir que un chofer tenga dos viajes en curso al mismo tiempo.", "Chofer"],
    ["RF06", "Registrar paradas, descansos, carga y descarga con hora y ubicación GPS automáticas.", "Chofer"],
    ["RF07", "Registrar gastos: combustible (monto y litros), peajes, alimentación, mantención y otros.", "Chofer"],
    ["RF08", "Registrar novedades (incidente, falla mecánica, retraso, accidente, clima, otro) con su gravedad.", "Chofer"],
    ["RF09", "Avisar cuando el manejo continuo se acerca a 5 horas (30 minutos antes) y cuando las supera; un descanso de 30 minutos o más reinicia el conteo.", "Chofer"],
    ["RF10", "Cerrar el viaje con el kilometraje final, quién recibe y una foto de la guía o la firma en pantalla.", "Chofer"],
    ["RF11", "Mostrar el resumen del viaje: km, duración, gastos por tipo, ganancia y combustible estimado frente al cargado.", "Ambos"],
    ["RF12", "Listar los viajes propios con filtros y ver la hoja de ruta de cada uno.", "Chofer"],
    ["RF13", "Corregir los datos de un viaje observado por el administrador.", "Chofer"],
    ["RF14", "Sincronizar sola cuando hay red y permitir \"Sincronizar ahora\" desde el perfil.", "Chofer"],
    ["RF15", "Ver la lista de viajes de todos los choferes, actualizada en vivo, con filtros por período, estado, validación y chofer.", "Administrador"],
    ["RF16", "Ver el detalle de un viaje y aprobar u observar su bitácora con un comentario.", "Administrador"],
    ["RF17", "Ver reportes mensuales de km, gastos y ganancia por chofer y por camión.", "Administrador"],
    ["RF18", "Ver en la lista de viajes cuáles superaron las horas de manejo continuo.", "Administrador"],
    ["RF19", "Agregar, editar y desactivar camiones, validando el formato de la patente.", "Administrador"],
    ["RF20", "Ver el perfil, el estado de sincronización, cambiar a tema claro y cerrar sesión.", "Ambos"],
  ]),
  pieTabla("Tabla 2. Requerimientos funcionales."),
  h2("7.2 Requerimientos no funcionales"),
  tabla([900, 2000, 6126], [
    ["ID", "Tipo", "Requerimiento"],
    ["RNF01", "Disponibilidad sin red", "Todo lo que hace el chofer funciona sin internet. Los datos se guardan primero en el teléfono y se envían solos cuando vuelve la señal."],
    ["RNF02", "Integridad", "Un registro no se pierde ni se duplica aunque el envío se corte a la mitad y se reintente."],
    ["RNF03", "Seguridad", "Contraseñas administradas por el servicio de autenticación (con hash), comunicación solo por HTTPS, sesión cifrada en el teléfono y permisos aplicados en la base de datos."],
    ["RNF04", "Usabilidad", "Botones grandes, pocos toques por registro, textos simples y un tema claro para leer a pleno sol."],
    ["RNF05", "Rendimiento", "Guardar un registro toma menos de un segundo; si el GPS no responde en 3 segundos, se guarda igual sin ubicación."],
    ["RNF06", "Compatibilidad", "Android 8.0 (API 26) o superior, en teléfonos de gama baja y media."],
    ["RNF07", "Mantenibilidad", "Arquitectura por capas (MVVM), código versionado en Git y 189 pruebas automatizadas."],
    ["RNF08", "Privacidad", "Los datos de la aplicación no se copian al respaldo de Google y se borran del teléfono al cerrar sesión."],
    ["RNF09", "Costo", "Funciona con servicios en plan gratuito; no requiere servidor propio."],
  ]),
  pieTabla("Tabla 3. Requerimientos no funcionales."),

  // 8
  h1("8. Alcance y limitaciones del proyecto"),
  h2("8.1 Alcance"),
  p("El proyecto entrega una aplicación Android con dos roles. El chofer registra el viaje completo, desde la revisión del camión hasta la entrega con foto o firma, con o sin señal. El administrador revisa y aprueba las bitácoras, consulta reportes mensuales y mantiene los camiones. Los datos viven en Supabase, un servicio en la nube con base de datos PostgreSQL, autenticación y almacenamiento de archivos. La aplicación se entrega como un instalador APK firmado, listo para el teléfono del cliente."),
  h2("8.2 Limitaciones"),
  v("No se conecta al computador del camión (OBD-II). El kilometraje y los gastos los ingresa el chofer."),
  v("No hace seguimiento GPS continuo. La ubicación se guarda solo cuando se registra algo."),
  v("No incluye facturación, pagos ni conexión con el Servicio de Impuestos Internos."),
  v("No calcula peajes de forma automática; se ingresan a mano. El combustible se estima con los kilómetros y el rendimiento del camión."),
  v("No tiene panel web ni versión para iOS. El administrador usa la misma aplicación Android."),
  v("No se publica en la tienda Google Play, porque una cuenta nueva exige 14 días de prueba cerrada con al menos 12 personas, y eso no cabe en el semestre."),
  v("El plan gratuito de Supabase pausa el proyecto tras una semana sin uso y no incluye respaldos automáticos descargables (ver sección 16)."),

  // 9
  h1("9. Organización del grupo proyecto"),
  h2("9.1 Roles y responsabilidades"),
  p([pend("[Propuesta de roles según el trabajo realizado; el grupo debe confirmarla o corregirla.]")]),
  tabla([2600, 2300, 4126], [
    ["Integrante", "Rol", "Responsabilidades"],
    ["Ignacio Moreno Aguirre", "Jefe técnico y desarrollador", "Arquitectura, programación de la aplicación, base de datos y sincronización, pruebas automatizadas, redacción técnica del informe y contacto con el cliente."],
    ["Nicolás Sepúlveda Catalán", "Coordinador de documentación", "Documento compartido del informe, primera propuesta (informe 1.1), revisión de redacción y formato."],
    ["Francisco Jeria Guerra", "Analista y pruebas", "Revisión de requerimientos, pruebas funcionales siguiendo el guion de demostración y registro de errores."],
    ["Benjamín Hernández", "Presentación y video", "Diapositivas de la presentación final y grabación y edición del video del software."],
    ["Claudio Pizarro Cerda", "Planificación y riesgos", "Carta Gantt, minutas de reunión, análisis de riesgos y seguimiento de plazos."],
  ]),
  pieTabla("Tabla 4. Roles del grupo."),
  h2("9.2 Toma de decisiones y distribución del trabajo"),
  p("Las decisiones de alcance (qué hace y qué no hace la aplicación) se tomaron en grupo, en las reuniones de avance y por el chat del grupo. Las decisiones técnicas (lenguaje, base de datos, arquitectura) las propuso el jefe técnico y el grupo las revisó antes de seguir. El trabajo se repartió por entregables: el código en un repositorio de GitHub, el informe en un documento compartido de Google Docs y la presentación en archivos aparte. Cada integrante revisó lo que hicieron los demás y dejó sus comentarios en el documento o en el chat."),

  // 10
  h1("10. Fases del proyecto"),
  tabla([2300, 4226, 2500], [
    ["Fase", "Actividades realizadas", "Evidencias"],
    ["0. Lanzamiento\n28 oct a 4 nov", "Formación del grupo, elección del tema a partir de la aplicación anterior, primera propuesta y reunión con la profesora.", "Informe 1.1 del grupo; minutas de reunión (anexo A)."],
    ["1. Diseño e implementación\n4 a 10 nov", "Levantamiento con el cliente, revisión de la aplicación anterior, decisión de rehacerla como app nativa con modo sin señal, diseño de arquitectura y datos, maquetas de dos estilos visuales y plan de implementación en 16 tareas.", "Documento de diseño y plan en el repositorio; informe 1.2; maquetas."],
    ["2. Desarrollo y producción\n11 a 15 nov", "Base local y flujo del chofer sin señal; base de datos en Supabase con reglas de seguridad; sincronización; inicio de sesión; pantallas del administrador; reportes; foto o firma en la entrega.", "40 commits en GitHub; capturas de pantalla (sección 12)."],
    ["3. Validación\n14 a 24 nov", "Pruebas automatizadas, recorridos completos en modo avión contra la base real, revisión de código de toda la rama y corrección de lo encontrado, APK firmado y pruebas con el cliente.", "Resultados de pruebas; guion de demostración (anexo D); APK de entrega."],
    ["Post. Evaluación e informes\n15 nov a 2 dic", "Informe final, presentación, video del software y entrega en Canvas.", "Este informe; presentación y video."],
  ]),
  pieTabla("Tabla 5. Fases del proyecto, actividades y evidencias."),
  p("La fase 2 avanzó más rápido de lo planificado (ver sección 15), porque la arquitectura y el plan quedaron muy detallados en la fase 1 y porque las reglas más delicadas, como la sincronización, se escribieron primero como pruebas automatizadas. Ese tiempo ganado se usó en la fase 3, que en el plan original era la más apretada."),

  // 11
  h1("11. Diseño del sistema"),
  h2("11.1 Metodología utilizada"),
  h3("Para el desarrollo del proyecto"),
  p("Usamos un enfoque híbrido. El alcance y las fechas se planificaron por fases y entregables, como en un modelo tradicional, porque la asignatura tiene hitos fijos. Dentro de la fase de desarrollo trabajamos en ciclos cortos de uno o dos días: cada ciclo terminaba con una parte de la aplicación funcionando y probada, y con eso se ajustaba el siguiente."),
  h3("Para el diseño del producto"),
  p("Seguimos Design Thinking. Primero entendimos cuándo usa el teléfono el chofer: al salir, en ruta y al entregar, casi siempre con poco tiempo. Con eso definimos flujos de pocos toques y botones grandes. Luego hicimos maquetas de dos estilos visuales y elegimos uno inspirado en la señalética de las autopistas chilenas: fondo oscuro, el viaje en curso dibujado como un letrero verde de autopista, los registros como hitos kilométricos en un camino y el color ámbar reservado para la acción principal de cada pantalla."),
  h3("Para la programación"),
  p("Aplicamos prácticas de Extreme Programming. Cada regla de negocio (cálculos, validaciones, base local y sincronización) se escribió primero como prueba automatizada y después se programó. El código se organizó en capas con el patrón MVVM, se versionó en Git con cambios pequeños y se compiló y probó en cada paso. Al terminar, una revisión de código de toda la rama buscó errores de sincronización, seguridad y estabilidad."),
  h2("11.2 Requerimientos del sistema"),
  h3("Software utilizado"),
  tabla([2300, 3100, 1500, 1200, 926], [
    ["Componente", "Software", "Versión", "Licencia", "Costo"],
    ["Lenguaje e interfaz", "Kotlin, Jetpack Compose y Material 3", "2.4 / BOM 2026.09", "Apache 2.0", "$0"],
    ["Navegación", "Navigation 3", "1.2", "Apache 2.0", "$0"],
    ["Base de datos local", "Room (SQLite)", "2.8", "Apache 2.0", "$0"],
    ["Tareas en segundo plano", "WorkManager", "2.12", "Apache 2.0", "$0"],
    ["Nube", "Supabase: PostgreSQL, Auth, Storage, Realtime", "Plan gratuito", "Apache 2.0 / servicio", "$0"],
    ["Cliente de la nube", "supabase-kt y Ktor", "3.9 / 3.6", "MIT / Apache 2.0", "$0"],
    ["Ubicación", "Google Play Services Location", "21.4", "Gratuita", "$0"],
    ["Entorno de desarrollo", "Android Studio con Gradle 9.9 y JDK 25", "2026", "Gratuita", "$0"],
    ["Control de versiones", "Git y GitHub", "2.x", "GPLv2 / servicio", "$0"],
    ["Documentación", "Google Docs", "-", "Servicio", "$0"],
  ]),
  pieTabla("Tabla 6. Software utilizado."),
  h3("Sistema operativo y hardware requerido"),
  tabla([2400, 4200, 2426], [
    ["Equipo", "Requisito", "Uso"],
    ["Teléfono del chofer", "Android 8.0 o superior, GPS, cámara, 2 GB de RAM y 100 MB libres", "Registrar los viajes"],
    ["Teléfono del administrador", "Android 8.0 o superior con conexión a internet", "Revisar y aprobar bitácoras"],
    ["Computador de desarrollo", "Windows 11, procesador de 4 núcleos, 16 GB de RAM, SSD", "Programar, compilar y usar el emulador"],
    ["Servidor", "Supabase en la nube (región São Paulo)", "Base de datos, autenticación y archivos"],
  ]),
  pieTabla("Tabla 7. Sistema operativo y hardware."),
  h3("Arquitectura"),
  p("La Figura 3 resume la arquitectura. Las pantallas del chofer leen y escriben siempre en la base de datos del teléfono, nunca directo en internet. Un trabajo en segundo plano envía lo pendiente a Supabase cuando hay red y descarga los cambios, como la aprobación del administrador. Cada registro recibe en el teléfono un identificador UUID definitivo, y el envío inserta o actualiza según ese identificador, de modo que reintentar no crea duplicados."),
  ...figura("07-arquitectura.png", 560, "Figura 3. Arquitectura del sistema."),
  h2("11.3 Modelos"),
  h3("Modelo entidad-relación"),
  p("El modelo tiene ocho entidades (Figura 4). Un perfil corresponde a una cuenta, con rol chofer o administrador. Un chofer realiza muchos viajes y cada viaje usa un camión. Cada viaje tiene una revisión del camión y puede tener muchos eventos (inicio, paradas, descansos, entrega, fin), gastos, novedades y adjuntos (foto de la guía o firma). Un administrador valida muchos viajes."),
  ...figura("03-modelo-er.png", 600, "Figura 4. Modelo entidad-relación."),
  h3("Modelo físico-relacional"),
  p("La Figura 5 muestra las tablas tal como están en PostgreSQL, con sus tipos de datos, claves primarias y foráneas. Las claves son UUID generados en el teléfono. Los estados (rol, estado del viaje, validación, tipos de evento, gasto y novedad, gravedad) son tipos enumerados, así la base de datos rechaza cualquier valor que no esté en la lista. Las reglas de negocio también viven en la base: el kilometraje final no puede ser menor que el inicial, la llegada no puede ser anterior a la salida y la patente debe tener el formato chileno."),
  ...figura("04-modelo-fisico.png", 600, "Figura 5. Modelo físico-relacional."),
  h3("Modelo físico-relacional normalizado"),
  p("El punto de partida fue la bitácora en papel: una hoja por viaje con datos del chofer, del camión, del flete y listas de gastos y paradas que se repiten. La Figura 6 resume cómo se normalizó."),
  ...figura("05-normalizacion.png", 600, "Figura 6. Normalización de la bitácora en papel."),
  v([b("Primera forma normal. "), run("Los gastos, eventos, novedades y adjuntos dejaron de ser listas dentro del viaje y pasaron a tablas propias, con una fila por registro y la clave foránea viaje_id. Cada columna guarda un solo valor.")]),
  v([b("Segunda forma normal. "), run("Todas las tablas usan una clave primaria simple (id), así que ningún atributo puede depender solo de una parte de la clave.")]),
  v([b("Tercera forma normal. "), run("La marca, el modelo y el rendimiento dependen del camión y no del viaje, por eso están en camiones. El nombre, RUT y teléfono dependen de la persona y están en perfiles. El viaje solo guarda las claves foráneas.")]),
  p("Los valores calculados, como los kilómetros recorridos, el gasto total o la ganancia, no se guardan en ninguna tabla. Los calcula la vista resumen_viajes cada vez que se consulta, para que nunca queden datos que se contradigan. El modelo de la Figura 5 ya está en tercera forma normal y es el que funciona en producción."),

  // 12
  h1("12. Desarrollo del sistema"),
  h2("12.1 Login"),
  p("Al abrir la aplicación por primera vez aparece la pantalla de inicio de sesión. El usuario entra con su correo y contraseña. Un chofer nuevo puede crear su cuenta con su nombre, correo y una contraseña de al menos 8 caracteres con letras y números; ese paso es el único que necesita conexión. Si olvida la contraseña, recibe un correo con un enlace que abre la aplicación en la pantalla para elegir una nueva."),
  p("Las contraseñas las administra el servicio de autenticación de Supabase, que guarda solo su hash; la aplicación nunca las almacena. Después del primer inicio de sesión, la sesión queda guardada en el teléfono cifrada con una llave del Android Keystore, así el chofer puede abrir la aplicación y trabajar aunque esté sin señal. Al cerrar sesión se borran del teléfono los datos, las fotos y las firmas; si quedan registros sin enviar, la aplicación lo advierte antes."),
  h2("12.2 Niveles de acceso y acciones de usuario"),
  p("Cada cuenta tiene un rol y la aplicación muestra un menú distinto a cada uno. La base de datos también aplica los permisos, con seguridad por fila (RLS) y triggers que validan cada operación, así que llamar directo al servidor sin pasar por la aplicación no permite hacer nada más."),
  tabla([4426, 2300, 2300], [
    ["Acción", "Chofer", "Administrador"],
    ["Ver viajes", "Solo los propios", "Todos"],
    ["Crear viajes y registrar eventos, gastos y novedades", "Sí, en sus viajes en curso", "No"],
    ["Corregir un viaje observado", "Sí", "No"],
    ["Modificar un viaje aprobado o sus registros", "No (lo bloquea la base de datos)", "Sí"],
    ["Aprobar u observar bitácoras", "No", "Sí"],
    ["Cambiar su propio rol", "No", "No (solo por SQL)"],
    ["Ver reportes mensuales y alertas de horas", "Resumen propio del mes", "Sí, de todos"],
    ["Agregar, editar o desactivar camiones", "No (solo los ve)", "Sí"],
    ["Ver fotos y firmas de la entrega", "Solo de sus viajes", "Todas"],
  ]),
  pieTabla("Tabla 8. Acciones permitidas por rol."),
  h2("12.3 Vistas del sistema"),
  p("Las capturas se tomaron en un teléfono emulado conectado a la base de datos real del proyecto. Las del chofer muestran el flujo de un viaje en orden; varias se tomaron en modo avión."),
  capturas([["00-login.png", "Inicio de sesión"], ["01-inicio-sin-viaje.png", "Inicio del chofer"], ["02-nuevo-viaje-camion.png", "Nuevo viaje: camión"]]),
  capturas([["03-revision-camion.png", "Revisión del camión"], ["04-inicio-viaje-en-curso.png", "Viaje en curso, sin señal"], ["05-registrar-combustible.png", "Carga de combustible"]]),
  capturas([["06-hoja-de-ruta.png", "Hoja de ruta con hitos"], ["13-entrega-con-firma.png", "Entrega con firma"], ["07-resumen-viaje.png", "Resumen del viaje"]]),
  capturas([["09-admin-viajes.png", "Viajes (administrador)"], ["12-admin-firma-entrega.png", "Detalle con la firma"], ["14-viaje-observado.png", "Viaje observado (chofer)"]]),
  h2("12.4 Reportes del sistema"),
  v([b("Resumen del viaje. "), run("Al cerrar un viaje, el chofer y el administrador ven los kilómetros recorridos, la duración, los gastos por tipo, la ganancia (precio del flete menos gastos) y el combustible cargado frente al estimado según los kilómetros y el rendimiento del camión. Esa comparación ayuda a detectar un consumo anormal o un gasto mal anotado.")]),
  v([b("Resumen del mes del chofer. "), run("En la pantalla de inicio el chofer ve los kilómetros, los gastos y la cantidad de viajes del mes.")]),
  v([b("Reportes mensuales del administrador. "), run("Muestran para el mes elegido la cantidad de viajes, los kilómetros, los gastos, la ganancia y cuántas bitácoras están aprobadas, por revisar u observadas, con barras por chofer y por camión que se pueden ordenar por kilómetros o por ganancia.")]),
  v([b("Alertas de horas de manejo. "), run("En la lista de viajes, los que superaron las 5 horas de manejo continuo sin un descanso de al menos 30 minutos aparecen marcados con un reloj rojo.")]),
  capturas([["11-admin-reportes.png", "Reportes mensuales"], ["10-admin-bitacora-aprobada.png", "Bitácora aprobada"], ["08-sincronizado-supabase.png", "Todo enviado"]]),

  // 13
  h1("13. Análisis de riesgos"),
  h2("13.1 Riesgos y planes de mitigación"),
  tabla([2500, 1000, 900, 3226, 1400], [
    ["Riesgo", "Prob.", "Impacto", "Mitigación", "Resultado"],
    ["Pérdida o duplicación de datos al sincronizar con señal intermitente", "Media", "Alto", "UUID generados en el teléfono, envíos repetibles sin duplicar, marca de pendiente que solo se borra si el registro no cambió durante el envío, y pruebas de cortes de red.", "Controlado. La revisión final encontró dos casos borde que se corrigieron."],
    ["Plazo corto para construir la aplicación completa", "Alta", "Alto", "Plan de 16 tareas con prioridad al modo sin señal y al flujo del chofer; trabajo en ciclos cortos.", "Controlado. El desarrollo terminó antes de lo planificado."],
    ["El cliente no usa la aplicación en ruta", "Media", "Alto", "Flujos de pocos toques, botones grandes, aviso de horas útil para él y una guía rápida de uso.", "Por medir en las pruebas con el cliente."],
    ["GPS sin señal o permiso denegado", "Media", "Medio", "El registro se guarda igual, sin coordenadas, después de 3 segundos.", "Controlado."],
    ["Cambios en las herramientas (Gradle, Android, Supabase)", "Media", "Medio", "Versiones fijas y documentadas; se ajustó el diseño cuando Supabase cambió sus reglas de correo.", "Ocurrió dos veces (sección 14)."],
    ["Acceso indebido a datos de otro usuario", "Baja", "Alto", "Seguridad por fila en todas las tablas, triggers en el servidor y pruebas con cuentas de cada rol.", "Controlado."],
    ["Participación desigual en el grupo", "Media", "Medio", "Roles definidos por entregable y revisión cruzada del trabajo.", "Parcial (sección 17)."],
  ]),
  pieTabla("Tabla 9. Riesgos del proyecto."),
  h2("13.2 Conclusiones sobre los riesgos"),
  p("El riesgo técnico más grande era la sincronización sin señal, por eso se trabajó primero y con pruebas automatizadas antes de escribir las pantallas. Los problemas que aparecieron después fueron casos borde, como una foto de entrega que llega al servidor cuando el viaje ya está aprobado. Los riesgos que más se materializaron vinieron de afuera, con cambios en las herramientas y en el servicio de la nube, y se resolvieron ajustando el diseño sin mover la fecha de entrega. Sigue abierto el riesgo de adopción, que solo se podrá medir cuando el cliente use la aplicación en sus viajes."),

  // 14
  h1("14. Manejo de crisis"),
  tabla([2300, 1300, 3226, 2200], [
    ["Problema", "Tiempo de solución", "Acciones", "Efectos colaterales"],
    ["El proyecto no compilaba: el entorno de Android Studio trae JDK 25 y la versión de Gradle de la aplicación anterior no lo soporta.", "Unas horas, el primer día de desarrollo.", "Se actualizó a Gradle 9.9 y al plugin de Android 9.5, que cambia la forma de configurar Kotlin, y se rehízo la configuración del proyecto.", "Se descartó reutilizar el proyecto anterior tal cual; se rehízo la estructura completa."],
    ["La propuesta inicial (informe 1.1) hablaba de un sistema web con un servidor propio en Railway y no contemplaba el trabajo sin señal.", "Dos días de análisis.", "Se revisó con el grupo y se cambió a una aplicación nativa con base local y Supabase, sin servidor propio. El grupo estuvo de acuerdo.", "Se reescribió el informe (versión 1.2) y se replanificó el desarrollo."],
    ["Supabase dejó de permitir editar las plantillas de correo sin un servidor de correo propio, y la recuperación de contraseña estaba diseñada con un código de 6 dígitos.", "Medio día.", "Se cambió a un enlace que abre la aplicación (bitacora://recuperar) con el flujo seguro PKCE.", "Se modificó el diseño y una pantalla."],
    ["En la prueba completa contra la base real, un chofer recién registrado no veía los camiones.", "Una hora.", "Se encontró que la navegación cancelaba la descarga al cambiar de pantalla y se protegió esa operación.", "Ninguno; se detectó antes de la demostración."],
    ["La revisión final encontró que, si el administrador aprueba un viaje antes de que llegue la foto de la entrega, el servidor la rechazaba para siempre.", "Una tarde.", "Se ajustó la regla del servidor para aceptar registros nuevos de un viaje aprobado sin permitir cambiar los existentes, y la aplicación dejó de reintentar sin fin los rechazos permanentes.", "Se aplicó una migración a la base de datos en producción."],
  ]),
  pieTabla("Tabla 10. Crisis del proyecto."),
  p("Ninguna crisis movió la fecha de entrega. En todas, lo que más ayudó fue tener pruebas automatizadas: permitieron cambiar partes delicadas del sistema y comprobar en minutos que el resto seguía funcionando."),

  // 15
  h1("15. Plan de trabajo"),
  h2("15.1 Cronograma de actividades"),
  p("La Figura 7 muestra la carta Gantt con el plan original y la ejecución real. La línea roja marca el 15 de noviembre."),
  ...figura("06-carta-gantt.png", 610, "Figura 7. Carta Gantt: plan y ejecución real."),
  h2("15.2 Plan estimado frente al real y responsables"),
  tabla([2700, 1500, 1500, 3326], [
    ["Tarea", "Plan", "Real", "Responsables"],
    ["Propuesta 1.1", "28 oct a 3 nov", "28 oct a 4 nov", "Todo el grupo"],
    ["Requerimientos y alcance", "4 a 7 nov", "4 a 10 nov", "Ignacio, Francisco"],
    ["Diseño y plan de implementación", "8 a 10 nov", "10 nov", "Ignacio"],
    ["Informe 1.2", "10 nov", "11 nov", "Ignacio, Nicolás"],
    ["Flujo del chofer sin señal", "11 a 13 nov", "11 nov", "Ignacio"],
    ["Nube, seguridad y sincronización", "12 a 16 nov", "13 nov", "Ignacio"],
    ["Administrador y reportes", "17 a 20 nov", "14 a 15 nov", "Ignacio"],
    ["Foto o firma y corrección", "19 a 21 nov", "15 nov", "Ignacio"],
    ["Pruebas y revisión de código", "21 a 24 nov", "14 a 16 nov", "Ignacio, Francisco"],
    ["Pruebas con el cliente", "22 a 25 nov", "17 a 24 nov", "Ignacio, Francisco"],
    ["Minutas, Gantt y riesgos", "Todo el semestre", "Todo el semestre", "Claudio"],
    ["Informe final", "25 nov a 1 dic", "15 nov a 1 dic", "Ignacio, Nicolás, Claudio"],
    ["Presentación y video", "25 nov a 1 dic", "24 nov a 1 dic", "Benjamín, todo el grupo"],
  ]),
  pieTabla("Tabla 11. Plan estimado y real por tarea."),
  h2("15.3 Costos en tiempo"),
  tabla([3200, 1900, 1900, 2026], [
    ["Fase", "Horas estimadas", "Horas reales", "Diferencia"],
    ["0. Lanzamiento", "10", "12", "+2"],
    ["1. Diseño e implementación", "20", "18", "-2"],
    ["2. Desarrollo y producción", "60", "34", "-26"],
    ["3. Validación", "20", "16", "-4"],
    ["Post. Informes, presentación y video", "25", "25", "0"],
    ["Total", "135", "105", "-30"],
  ]),
  pieTabla("Tabla 12. Horas por fase, sumando a todo el grupo."),
  p([pend("[El grupo debe confirmar las horas reales de cada integrante antes de la entrega.]")]),
  h2("15.4 Costos en dinero"),
  tabla([3400, 1900, 1900, 1826], [
    ["Ítem", "Estimado", "Real", "Comentario"],
    ["Supabase (base de datos, autenticación y archivos)", "$0", "$0", "Plan gratuito"],
    ["Cuenta de Google Play Console", "$24.000", "$0", "Se entregó APK; no se publicó en la tienda"],
    ["Equipos (computador y teléfonos)", "$0", "$0", "Equipos propios"],
    ["Software y librerías", "$0", "$0", "Todo gratuito o de código abierto"],
    ["Horas de trabajo valorizadas a $5.000 por hora", "$675.000", "$525.000", "Costo de oportunidad, no se pagó"],
    ["Total", "$699.000", "$525.000", ""],
  ]),
  pieTabla("Tabla 13. Costos en dinero, estimado y real (pesos chilenos)."),
  p("El desembolso real fue cero. Si el cliente quisiera mantener el sistema en producción con respaldos automáticos, el costo recurrente sería el plan Pro de Supabase, de 25 dólares al mes (unos $24.000)."),
  h2("15.5 Estado del proyecto"),
  p("El proyecto está finalizado en lo comprometido: todas las funciones de los requerimientos RF01 a RF20 están implementadas, probadas con pruebas automatizadas y verificadas en recorridos completos contra la base de datos real. Lo que queda corresponde a la fase post y a la puesta en marcha:"),
  v([pend("[Actualizar antes de la entrega] "), run("Prueba en el teléfono del cliente durante un viaje real.")]),
  v([pend("[Actualizar antes de la entrega] "), run("Grabación del video y presentación final, el 2 de diciembre.")]),

  // 16
  h1("16. Plan de continuidad y riesgos post producción"),
  tabla([2500, 3000, 3526], [
    ["Riesgo", "Implicancia en la operación", "Mantenimiento y mitigación"],
    ["El plan gratuito de Supabase pausa el proyecto después de 7 días sin uso.", "Si el chofer no sincroniza en una semana, la app no puede enviar datos hasta reactivarlo.", "El uso normal lo mantiene activo. Si hay pausas largas, reactivarlo desde el panel o pasar al plan Pro."],
    ["El plan gratuito no tiene respaldos descargables.", "Un error humano en la base de datos no se podría deshacer.", "Exportar la base con pg_dump una vez al mes y guardar la copia fuera de Supabase."],
    ["Pérdida o robo del teléfono con viajes sin enviar.", "Se pierden los registros que no alcanzaron a subir.", "Sincronizar cada vez que haya señal; la app avisa cuántos registros faltan. La sesión está cifrada."],
    ["Pérdida de la llave de firma del APK.", "No se pueden instalar actualizaciones sobre la versión actual.", "Guardar la llave y su clave en dos lugares seguros, fuera del repositorio."],
    ["Nuevas versiones de Android o de las librerías.", "La aplicación podría dejar de compilar o tener fallas.", "Revisar dependencias cada seis meses y correr las pruebas automatizadas antes de publicar."],
    ["El sistema depende de una sola persona que conoce el código.", "Sin ella, mantenerlo sería lento.", "README, documento de diseño, guía de configuración de Supabase y pruebas en el repositorio."],
  ]),
  pieTabla("Tabla 14. Riesgos después de la puesta en producción."),
  p("El mantenimiento recomendado es sencillo: una revisión mensual del panel de Supabase y del respaldo, y una actualización de dependencias cada seis meses. Si la empresa suma camiones o choferes, el sistema ya está preparado: basta con crear las cuentas y registrar los camiones."),

  // 17
  h1("17. Conclusiones"),
  h2("17.1 Consecuencias de la implementación en la empresa"),
  tabla([2400, 3300, 3326], [
    ["Aspecto", "Antes", "Después"],
    ["Registro del viaje", "Cuaderno y boletas sueltas", "Aplicación en el teléfono, con hora y ubicación automáticas"],
    ["Gastos", "Se suman a mano al volver", "Se registran al pagar y se suman solos"],
    ["Ganancia del viaje", "Se calcula días después", "Aparece al cerrar el viaje"],
    ["Comprobante de entrega", "Guía en papel", "Foto de la guía o firma en pantalla, guardada en la nube"],
    ["Horas de manejo", "A criterio del chofer", "Aviso 30 minutos antes de llegar a 5 horas continuas"],
    ["Historial", "En papel, se puede perder", "En la nube, con reportes por mes"],
  ]),
  pieTabla("Tabla 15. La empresa antes y después del sistema."),
  h2("17.2 Problemas enfrentados durante la ejecución"),
  h3("Por interacción con el cliente"),
  p("Transportes MA SpA es la empresa del padre de uno de los integrantes, Ignacio Moreno. Eso hizo fácil explicarle el proyecto y obtener su autorización para usar la empresa como caso real. La dificultad estuvo en los tiempos: el cliente pasa gran parte del mes en ruta, así que las conversaciones se concentraron en los días en que estaba en casa y había que llegar a cada una con preguntas concretas. También hubo que traducir su forma de trabajar, muy práctica y basada en el papel, a pantallas que no le pidieran más trabajo que el cuaderno."),
  h3("Por dinámica interna del equipo"),
  p("La primera propuesta se escribió antes de que todo el grupo conociera la aplicación anterior, y por eso describía un sistema web que no calzaba con el problema. Corregirlo exigió reescribir buena parte del informe. Además, el desarrollo quedó concentrado en un integrante, porque requería conocer Kotlin y Android; el resto del grupo se enfocó en la documentación, las pruebas, la planificación y la presentación. Para la próxima vez convendría que más integrantes participen en el código desde el comienzo, aunque sea en partes pequeñas."),
  h2("17.3 Lo aprendido y su aplicabilidad futura"),
  p("Aprendimos que en un sistema de información lo más difícil es el dato: dónde nace, cómo viaja y quién puede cambiarlo. Diseñar primero el modelo de datos, las reglas de seguridad y el comportamiento sin señal, y recién después las pantallas, nos ahorró rehacer trabajo. También aprendimos a poner las reglas importantes en la base de datos además de la aplicación, y a escribir pruebas antes del código en las partes delicadas. Todo esto se aplica a cualquier sistema que tenga usuarios en terreno, como despacho, mantención o ventas."),
  h2("17.4 Otros aspectos"),
  p("Trabajar con una empresa real, aunque sea pequeña, cambió la forma de tomar decisiones: cada función se discutió pensando en si Hernán la usaría en la cabina. Por la misma razón dejamos fuera cosas que sonaban bien en la propuesta, como el cálculo automático de peajes o el panel web, y pusimos el esfuerzo en que el registro sin señal fuera confiable."),

  // 18
  h1("18. Anexos", true),
  h2("Anexo A. Minutas de reunión"),
  p([pend("[El grupo debe adjuntar las minutas: fecha, asistentes, temas tratados, acuerdos y responsables de cada reunión.]")]),
  h2("Anexo B. Carta Gantt"),
  p("La carta Gantt está en la Figura 7 (sección 15.1) y se adjunta como archivo aparte en el comprimido de la entrega."),
  h2("Anexo C. Presentaciones de avance"),
  p([pend("[Adjuntar las presentaciones de avance mostradas en clases.]")]),
  h2("Anexo D. Guion de demostración"),
  p("El guion completo está en el repositorio, en docs/demo.md. Resume el recorrido que se usa para mostrar el sistema:"),
  n("El chofer, en modo avión, crea un viaje, registra la revisión, una parada, un descanso, combustible, un peaje y una novedad.", 2),
  n("Cierra el viaje con el kilometraje final y la firma de quien recibe; el respaldo queda \"Por enviar\".", 2),
  n("Al quitar el modo avión, la aplicación muestra \"Todo enviado\" sin que el chofer haga nada.", 2),
  n("El administrador ve el viaje en su lista, revisa el detalle con la firma y lo observa con un comentario.", 2),
  n("El chofer corrige el viaje, que vuelve a quedar por revisar, y el administrador lo aprueba.", 2),
  n("El administrador revisa los reportes del mes.", 2),
  h2("Anexo E. Repositorio y pruebas"),
  p("El código, el esquema de la base de datos y la documentación técnica están en el repositorio público github.com/IgnacioMorenoAguirre/bitacora-camionero. Al cierre de este informe, la aplicación tiene 149 pruebas automatizadas de lógica y 40 pruebas sobre la base de datos local, y todas pasan."),
];

const doc = new Document({
  creator: "Grupo Taller de Sistemas de Información",
  title: "Informe Final Proyecto SI - Bitácora del Camionero",
  styles: {
    default: { document: { run: { font: FUENTE, size: T }, paragraph: { spacing: { line: 360 } } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FUENTE, size: 32, bold: true, color: VERDE },
        paragraph: { spacing: { before: 240, after: 200, line: 360 }, keepNext: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FUENTE, size: 26, bold: true, color: "262626" },
        paragraph: { spacing: { before: 280, after: 120, line: 360 }, keepNext: true, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FUENTE, size: 24, bold: true, italics: true, color: "262626" },
        paragraph: { spacing: { before: 200, after: 80, line: 360 }, keepNext: true, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [
      { reference: "vinetas", levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
        { level: 1, format: LevelFormat.BULLET, text: "◦", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1440, hanging: 360 } } } },
      ] },
      { reference: "numeros", levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
      ] },
    ],
  },
  features: { updateFields: true },
  sections: [{
    properties: { titlePage: true, page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    footers: {
      first: new Footer({ children: [] }),
      default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [
        new TextRun({ children: [PageNumber.CURRENT], font: FUENTE, size: 20 })] })] }),
    },
    children: [...portada, ...indice, ...cuerpo],
  }],
});

Packer.toBuffer(doc).then(buf => { fs.writeFileSync(SALIDA, buf); console.log("ok", SALIDA, buf.length); });
