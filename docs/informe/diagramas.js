// Genera las figuras del informe final (SVG -> PNG) en docs/informe/figuras.
// Uso: node diagramas.js   (necesita @resvg/resvg-js instalado)
const fs = require("fs");
const path = require("path");
const { Resvg } = require("@resvg/resvg-js");

const SALIDA = path.join(__dirname, "figuras");
fs.mkdirSync(SALIDA, { recursive: true });

const VERDE = "#0E6B4C", AMBAR = "#F2A900", TINTA = "#1C1F22", GRIS = "#6B7178", LINEA = "#9AA0A6", FONDO_VERDE = "#E7F1EC";
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function svg(ancho, alto, cuerpo) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${ancho}" height="${alto}" viewBox="0 0 ${ancho} ${alto}" font-family="Arial">
<defs>
  <marker id="flecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="${GRIS}"/></marker>
</defs>
<rect width="100%" height="100%" fill="#FFFFFF"/>
${cuerpo}
</svg>`;
}
function texto(x, y, s, { tam = 14, peso = "normal", color = TINTA, ancla = "middle", estilo = "normal" } = {}) {
  return `<text x="${x}" y="${y}" font-size="${tam}" font-weight="${peso}" fill="${color}" text-anchor="${ancla}" font-style="${estilo}">${esc(s)}</text>`;
}
function caja(x, y, w, h, lineas, { relleno = "#FFFFFF", borde = VERDE, tam = 14, titulo = false, radio = 8 } = {}) {
  const alto = tam * 1.3;
  const y0 = y + h / 2 - ((lineas.length - 1) * alto) / 2 + tam * 0.35;
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${radio}" fill="${relleno}" stroke="${borde}" stroke-width="2"/>` +
    lineas.map((l, i) => texto(x + w / 2, y0 + i * alto, l, { tam, peso: titulo && i === 0 ? "bold" : "normal" })).join("");
}
const linea = (x1, y1, x2, y2, { flecha = true, color = GRIS, guion = false } = {}) =>
  `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="2" ${guion ? 'stroke-dasharray="6 4"' : ""} ${flecha ? 'marker-end="url(#flecha)"' : ""}/>`;
const poli = (pts, { flecha = true } = {}) =>
  `<polyline points="${pts.map(p => p.join(",")).join(" ")}" fill="none" stroke="${GRIS}" stroke-width="2" ${flecha ? 'marker-end="url(#flecha)"' : ""}/>`;

function guardar(nombre, contenido) {
  const png = new Resvg(contenido, { fitTo: { mode: "zoom", value: 2 }, font: { loadSystemFonts: true, defaultFontFamily: "Arial" } }).render().asPng();
  fs.writeFileSync(path.join(SALIDA, nombre + ".png"), png);
  console.log("ok", nombre);
}

// ---------------------------------------------------------------- organigrama
(function organigrama() {
  let c = "";
  c += caja(300, 20, 300, 60, ["Transportes MA SpA", "Transporte de carga por carretera"], { relleno: VERDE, borde: VERDE, titulo: true });
  c = c.replace(/fill="#1C1F22"/g, 'fill="#FFFFFF"');
  c += linea(450, 80, 450, 120, { flecha: false });
  c += caja(275, 120, 350, 70, ["Hernán Moreno", "Dueño, chofer y administración"], { titulo: true, relleno: FONDO_VERDE });
  const funciones = [
    ["Conducción", "Volvo FH con rampla"],
    ["Fletes y clientes", "Busca y acepta viajes"],
    ["Gastos y boletas", "Combustible, peajes, comida"],
    ["Mantención", "Revisión y talleres"],
  ];
  const w = 195, gap = 20, x0 = (900 - (4 * w + 3 * gap)) / 2;
  c += linea(450, 190, 450, 220, { flecha: false });
  c += linea(x0 + w / 2, 220, x0 + 3 * (w + gap) + w / 2, 220, { flecha: false });
  funciones.forEach(([a, b], i) => {
    const x = x0 + i * (w + gap);
    c += linea(x + w / 2, 220, x + w / 2, 245, { flecha: false });
    c += caja(x, 245, w, 62, [a, b], { titulo: true, tam: 13 });
  });
  guardar("01-organigrama", svg(900, 330, c));
})();

// ---------------------------------------------------------------- flujo actual
(function flujoActual() {
  const pasos = [
    ["Llega un flete", "por teléfono o WhatsApp"],
    ["Acuerda precio", "origen, destino y carga"],
    ["Revisa el camión", "y sale a la ruta"],
    ["Anota a mano", "km, horas y novedades"],
    ["Guarda cada boleta", "combustible, peajes, comida"],
    ["Entrega la carga", "guía firmada en papel"],
    ["De vuelta ordena", "cuaderno y boletas"],
    ["Calcula a mano", "gastos y ganancia del viaje"],
  ];
  let c = "";
  const w = 200, h = 64, gx = 40, gy = 60, x0 = 30, y0 = 30;
  pasos.forEach(([a, b], i) => {
    const fila = Math.floor(i / 4), col = fila === 0 ? i % 4 : 3 - (i % 4);
    const x = x0 + col * (w + gx), y = y0 + fila * (h + gy);
    const relleno = i >= 3 && i <= 4 ? "#FFF6DD" : "#FFFFFF";
    const borde = i >= 3 && i <= 4 ? AMBAR : VERDE;
    c += caja(x, y, w, h, [a, b], { titulo: true, tam: 13, relleno, borde });
    if (i < 3) c += linea(x + w, y + h / 2, x + w + gx - 2, y + h / 2);
    if (i === 3) c += linea(x + w / 2, y + h, x + w / 2, y + h + gy - 2);
    if (i >= 4 && i < 7) c += linea(x, y + h / 2, x - gx + 2, y + h / 2);
  });
  c += texto(495, 250, "En ámbar: los pasos donde la información queda solo en papel y se puede perder.", { tam: 13, color: GRIS, estilo: "italic" });
  guardar("02-flujo-actual", svg(990, 270, c));
})();

// ---------------------------------------------------------------- modelo ER (Chen simplificado)
(function modeloER() {
  let c = "";
  const ent = (x, y, nombre, attrs, w = 190) => {
    const h = 34 + attrs.length * 18;
    let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#FFFFFF" stroke="${VERDE}" stroke-width="2"/>`;
    s += `<rect x="${x}" y="${y}" width="${w}" height="28" fill="${VERDE}"/>`;
    s += texto(x + w / 2, y + 19, nombre, { tam: 14, peso: "bold", color: "#FFFFFF" });
    attrs.forEach((a, i) => {
      const clave = a.startsWith("*");
      s += texto(x + 10, y + 46 + i * 18, clave ? a.slice(1) : a, { tam: 12, ancla: "start", peso: clave ? "bold" : "normal" });
      if (clave) s += `<line x1="${x + 10}" y1="${y + 49 + i * 18}" x2="${x + 10 + (a.length - 1) * 6.6}" y2="${y + 49 + i * 18}" stroke="${TINTA}"/>`;
    });
    return { s, x, y, w, h, cx: x + w / 2, cy: y + h / 2 };
  };
  const rel = (cx, cy, nombre) => {
    const w = 64, h = 34;
    return `<polygon points="${cx - w},${cy} ${cx},${cy - h} ${cx + w},${cy} ${cx},${cy + h}" fill="#FFF6DD" stroke="${AMBAR}" stroke-width="2"/>` +
      texto(cx, cy + 5, nombre, { tam: 12, peso: "bold" });
  };
  const card = (x, y, s) => texto(x, y, s, { tam: 14, peso: "bold", color: VERDE });

  const perfil = ent(40, 40, "PERFIL", ["*id", "nombre_completo", "rut", "teléfono", "rol", "activo"]);
  const camion = ent(40, 420, "CAMIÓN", ["*id", "patente", "marca, modelo, año", "rendimiento_km_l", "activo"]);
  const viaje = ent(460, 200, "VIAJE", ["*id", "cliente, origen, destino", "ruta, tipo_carga, peso_kg", "precio", "fecha_salida, fecha_llegada", "km_inicial, km_final", "estado, validación"], 220);
  const revision = ent(900, 20, "REVISIÓN", ["*id", "neumáticos, luces, frenos", "niveles, documentos"]);
  const evento = ent(900, 150, "EVENTO", ["*id", "tipo, fecha_hora, fecha_fin", "latitud, longitud, km"]);
  const gasto = ent(900, 280, "GASTO", ["*id", "tipo, monto, litros", "fecha_hora"]);
  const novedad = ent(900, 395, "NOVEDAD", ["*id", "tipo, gravedad", "descripción, fecha_hora"]);
  const adjunto = ent(900, 510, "ADJUNTO", ["*id", "tipo (foto o firma)", "ruta_remota, receptor"]);

  // relaciones perfil-viaje (realiza y valida) y camión-viaje
  c += linea(perfil.x + perfil.w, perfil.y + 60, 330, perfil.y + 60, { flecha: false }) + linea(330, perfil.y + 60, 330, 225, { flecha: false });
  c += linea(330, 225, viaje.x, 225, { flecha: false });
  c += rel(330, 150, "realiza");
  c += card(perfil.x + perfil.w + 12, perfil.y + 52, "1") + card(viaje.x - 18, 218, "N");

  c += linea(perfil.x + perfil.w, perfil.y + 120, 290, perfil.y + 120, { flecha: false, guion: true });
  c += linea(290, perfil.y + 120, 290, 300, { flecha: false, guion: true }) + linea(290, 300, viaje.x, 300, { flecha: false, guion: true });
  c += rel(290, 270, "valida");
  c += card(perfil.x + perfil.w + 12, perfil.y + 140, "1") + card(viaje.x - 18, 293, "N");

  c += linea(camion.x + camion.w, camion.cy, 330, camion.cy, { flecha: false }) + linea(330, camion.cy, 330, 345, { flecha: false });
  c += linea(330, 345, viaje.x, 345, { flecha: false });
  c += rel(330, camion.cy - 10, "usa");
  c += card(camion.x + camion.w + 12, camion.cy - 8, "1") + card(viaje.x - 18, 338, "N");

  const hijos = [[revision, "tiene", "1", "1"], [evento, "registra", "1", "N"], [gasto, "registra", "1", "N"], [novedad, "registra", "1", "N"], [adjunto, "respalda", "1", "N"]];
  const xv = viaje.x + viaje.w;
  hijos.forEach(([e, nombre, a, b], i) => {
    const yv = viaje.y + 30 + i * 30;
    c += linea(xv, yv, 751, e.cy, { flecha: false }) + linea(879, e.cy, e.x, e.cy, { flecha: false });
    c += card(xv + 12, yv - 4, a) + card(e.x - 10, e.cy - 8, b);
  });
  // las cajas y rombos encima de las líneas
  c += [perfil, camion, viaje, revision, evento, gasto, novedad, adjunto].map(e => e.s).join("");
  hijos.forEach(([e, nombre]) => { c += rel(815, e.cy, nombre); });
  c += rel(330, 150, "realiza") + rel(290, 270, "valida") + rel(330, camion.cy - 10, "usa");
  c += texto(40, 600, "Atributo en negrita y subrayado: identificador.  Línea punteada: el administrador que aprueba u observa el viaje.", { tam: 12, color: GRIS, ancla: "start", estilo: "italic" });
  guardar("03-modelo-er", svg(1110, 615, c));
})();

// ---------------------------------------------------------------- modelo físico-relacional
const TABLAS = {
  perfiles: [["id", "uuid", "PK, FK auth.users"], ["nombre_completo", "text", "not null"], ["rut", "text", "unique"], ["telefono", "text", ""], ["rol", "rol_usuario", "chofer | admin"], ["activo", "boolean", ""], ["creado_en", "timestamptz", ""], ["actualizado_en", "timestamptz", ""]],
  camiones: [["id", "uuid", "PK"], ["patente", "text", "unique, formato"], ["marca", "text", "not null"], ["modelo", "text", "not null"], ["anio", "int", "1980 a 2100"], ["rendimiento_km_l", "numeric(5,2)", "> 0"], ["activo", "boolean", ""], ["creado_en / actualizado_en", "timestamptz", ""]],
  viajes: [["id", "uuid", "PK"], ["chofer_id", "uuid", "FK perfiles"], ["camion_id", "uuid", "FK camiones"], ["cliente", "text", "not null"], ["origen", "text", "not null"], ["destino", "text", "not null"], ["ruta", "text", ""], ["tipo_carga", "text", "not null"], ["peso_kg", "numeric(10,2)", "> 0"], ["requiere_refrigeracion", "boolean", ""], ["precio", "numeric(12,0)", ">= 0"], ["fecha_salida", "timestamptz", "not null"], ["fecha_llegada", "timestamptz", ">= salida"], ["km_inicial", "int", ">= 0"], ["km_final", "int", ">= km_inicial"], ["estado", "estado_viaje", ""], ["validacion", "estado_validacion", ""], ["comentario_validacion", "text", ""], ["validado_por", "uuid", "FK perfiles"], ["observaciones", "text", ""], ["eliminado", "boolean", ""], ["creado_en / actualizado_en", "timestamptz", ""]],
  revisiones: [["id", "uuid", "PK"], ["viaje_id", "uuid", "FK viajes, unique"], ["neumaticos, luces, frenos", "boolean", "not null"], ["niveles, documentos", "boolean", "not null"], ["comentario", "text", ""]],
  eventos: [["id", "uuid", "PK"], ["viaje_id", "uuid", "FK viajes"], ["tipo", "tipo_evento", ""], ["fecha_hora / fecha_fin", "timestamptz", ""], ["latitud / longitud", "double", "rango válido"], ["direccion", "text", ""], ["km", "int", ">= 0"], ["nota", "text", ""]],
  gastos: [["id", "uuid", "PK"], ["viaje_id", "uuid", "FK viajes"], ["tipo", "tipo_gasto", ""], ["monto", "numeric(12,0)", ">= 0"], ["litros", "numeric(8,2)", "> 0"], ["fecha_hora", "timestamptz", ""], ["descripcion", "text", ""]],
  novedades: [["id", "uuid", "PK"], ["viaje_id", "uuid", "FK viajes"], ["tipo", "tipo_novedad", ""], ["gravedad", "gravedad_novedad", ""], ["descripcion", "text", "not null"], ["fecha_hora", "timestamptz", ""], ["latitud / longitud", "double", ""]],
  adjuntos: [["id", "uuid", "PK"], ["viaje_id", "uuid", "FK viajes"], ["tipo", "text", "foto | firma"], ["ruta_remota", "text", "archivo en Storage"], ["nombre_receptor", "text", ""], ["fecha_hora", "timestamptz", ""]],
};
function tablaFisica(x, y, nombre, cols, w = 330) {
  const fh = 19, h = 30 + cols.length * fh + 6;
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#FFFFFF" stroke="${VERDE}" stroke-width="2"/>`;
  s += `<rect x="${x}" y="${y}" width="${w}" height="28" fill="${VERDE}"/>` + texto(x + 10, y + 19, nombre, { tam: 14, peso: "bold", color: "#FFFFFF", ancla: "start" });
  cols.forEach(([col, tipo, nota], i) => {
    const yy = y + 46 + i * fh;
    const pk = nota.startsWith("PK"), fk = nota.includes("FK");
    if (i % 2 === 1) s += `<rect x="${x + 1}" y="${yy - 14}" width="${w - 2}" height="${fh}" fill="#F4F6F5"/>`;
    s += texto(x + 8, yy, pk ? "PK" : fk ? "FK" : "", { tam: 10, peso: "bold", color: pk ? AMBAR : VERDE, ancla: "start" });
    s += texto(x + 32, yy, col, { tam: 12, ancla: "start", peso: pk ? "bold" : "normal" });
    s += texto(x + w - 8, yy, tipo, { tam: 11, ancla: "end", color: GRIS });
  });
  return { s, x, y, w, h };
}
(function modeloFisico() {
  let c = "";
  const perfiles = tablaFisica(30, 30, "perfiles", TABLAS.perfiles, 300);
  const camiones = tablaFisica(30, 470, "camiones", TABLAS.camiones, 300);
  const viajes = tablaFisica(420, 30, "viajes", TABLAS.viajes, 330);
  const derecha = []; let y = 30;
  for (const n of ["revisiones", "eventos", "gastos", "novedades", "adjuntos"]) {
    const t = tablaFisica(850, y, n, TABLAS[n], 320); derecha.push(t); y += t.h + 22;
  }
  // FKs: viajes.chofer_id -> perfiles.id ; viajes.validado_por -> perfiles.id ; viajes.camion_id -> camiones.id
  const filaY = (t, i) => t.y + 46 + i * 19 - 5;
  c += poli([[viajes.x, filaY(viajes, 1)], [375, filaY(viajes, 1)], [375, filaY(perfiles, 0)], [perfiles.x + perfiles.w + 2, filaY(perfiles, 0)]]);
  c += poli([[viajes.x, filaY(viajes, 18)], [390, filaY(viajes, 18)], [390, filaY(perfiles, 0) + 8], [perfiles.x + perfiles.w + 2, filaY(perfiles, 0) + 8]]);
  c += poli([[viajes.x, filaY(viajes, 2)], [360, filaY(viajes, 2)], [360, filaY(camiones, 0)], [camiones.x + camiones.w + 2, filaY(camiones, 0)]]);
  derecha.forEach((t, i) => {
    const xm = 800 + i * 8;
    c += poli([[t.x, filaY(t, 1)], [xm, filaY(t, 1)], [xm, filaY(viajes, 0) + i * 3], [viajes.x + viajes.w + 2, filaY(viajes, 0) + i * 3]]);
  });
  c += perfiles.s + camiones.s + viajes.s + derecha.map(t => t.s).join("");
  const alto = Math.max(y, viajes.y + viajes.h + 30, camiones.y + camiones.h + 30);
  c += texto(30, alto - 4, "Todas las tablas tienen además eliminado (boolean) y creado_en / actualizado_en. Flechas: clave foránea hacia la clave primaria.", { tam: 12, color: GRIS, ancla: "start", estilo: "italic" });
  guardar("04-modelo-fisico", svg(1200, alto + 10, c));
})();

// ---------------------------------------------------------------- normalización
(function normalizacion() {
  let c = "";
  c += texto(20, 28, "Bitácora en papel (sin normalizar): una hoja por viaje", { tam: 15, peso: "bold", ancla: "start" });
  const cab = ["Fecha", "Chofer", "Camión (patente, marca)", "Cliente / ruta", "Km", "Gastos (varios por hoja)", "Paradas (varias)"];
  const anchos = [90, 110, 190, 170, 70, 220, 170];
  let x = 20;
  cab.forEach((h, i) => {
    c += `<rect x="${x}" y="40" width="${anchos[i]}" height="30" fill="${VERDE}"/>` + texto(x + anchos[i] / 2, 60, h, { tam: 12, peso: "bold", color: "#FFFFFF" });
    const fila = ["12-10", "H. Moreno", "XX1234, Volvo FH", "Castro / 5 Sur", "1.180", "petróleo 380.000; peaje 21.300; ...", "Chillán 02:10; ..."][i];
    c += `<rect x="${x}" y="70" width="${anchos[i]}" height="30" fill="#FFF6DD" stroke="${AMBAR}"/>` + texto(x + anchos[i] / 2, 90, fila, { tam: 12 });
    x += anchos[i];
  });
  const pasos = [
    ["1FN", "Se separan los grupos repetidos", "Gastos, paradas, novedades y adjuntos pasan a sus propias tablas, una fila por registro, con viaje_id."],
    ["2FN", "Cada tabla tiene clave simple", "Toda tabla usa un id UUID como clave primaria, así que no hay dependencias parciales de una clave compuesta."],
    ["3FN", "Se quitan dependencias transitivas", "Marca, modelo y rendimiento dependen de la patente, no del viaje: van a camiones. Nombre y RUT van a perfiles."],
  ];
  pasos.forEach(([n, t1, t2], i) => {
    const y = 130 + i * 78;
    c += `<rect x="20" y="${y}" width="70" height="60" rx="8" fill="${VERDE}"/>` + texto(55, y + 37, n, { tam: 18, peso: "bold", color: "#FFFFFF" });
    c += texto(105, y + 24, t1, { tam: 14, peso: "bold", ancla: "start" });
    c += texto(105, y + 46, t2, { tam: 13, ancla: "start", color: "#3A3F44" });
  });
  c += texto(20, 375, "Los totales (km recorridos, gasto total, ganancia) no se guardan: los calcula la vista resumen_viajes, así no quedan datos que se contradigan.", { tam: 13, color: GRIS, ancla: "start", estilo: "italic" });
  guardar("05-normalizacion", svg(1060, 395, c));
})();

// ---------------------------------------------------------------- carta Gantt (plan vs real)
(function gantt() {
  const inicio = Date.UTC(2026, 9, 26); // lunes 26 oct
  const dia = 86400000, dias = 38; // hasta el 2 de diciembre
  const tareas = [
    ["Fase 0: Lanzamiento", null, null, null, true],
    ["Propuesta 1.1 y reuniones de partida", "28-10", "03-11", ["28-10", "04-11"]],
    ["Fase 1: Diseño e implementación", null, null, null, true],
    ["Requerimientos y alcance con el cliente", "04-11", "07-11", ["04-11", "10-11"]],
    ["Diseño: arquitectura, datos y maquetas", "08-11", "10-11", ["10-11", "10-11"]],
    ["Fase 2: Desarrollo y producción", null, null, null, true],
    ["Base local y flujo del chofer sin señal", "11-11", "13-11", ["11-11", "11-11"]],
    ["Nube, seguridad y sincronización", "12-11", "16-11", ["13-11", "13-11"]],
    ["Pantallas del administrador y reportes", "17-11", "20-11", ["14-11", "15-11"]],
    ["Foto o firma y corrección de viajes", "19-11", "21-11", ["15-11", "15-11"]],
    ["Fase 3: Validación", null, null, null, true],
    ["Pruebas de punta a punta y revisión", "21-11", "24-11", ["14-11", "16-11"]],
    ["Pruebas con el cliente en teléfono real", "22-11", "25-11", ["17-11", "24-11"], false, true],
    ["Fase Post: Evaluación e informes", null, null, null, true],
    ["Informe final, presentación y video", "25-11", "01-12", ["15-11", "01-12"], false, true],
    ["Presentación final", "02-12", "02-12", ["02-12", "02-12"], false, true],
  ];
  const aDia = (s) => { const [d, m] = s.split("-").map(Number); return Math.round((Date.UTC(2026, m - 1, d) - inicio) / dia); };
  const xIni = 330, colW = 21, fila = 26, y0 = 70;
  const ancho = xIni + dias * colW + 20, alto = y0 + tareas.length * fila + 60;
  let c = "";
  for (let d = 0; d < dias; d++) {
    const fecha = new Date(inicio + d * dia);
    const finde = [0, 6].includes(fecha.getUTCDay());
    c += `<rect x="${xIni + d * colW}" y="${y0 - 8}" width="${colW}" height="${tareas.length * fila + 8}" fill="${finde ? "#F3F4F5" : "#FFFFFF"}" stroke="#E3E5E8"/>`;
    if (fecha.getUTCDay() === 1 || d === 0) c += texto(xIni + d * colW + 2, y0 - 16, `${fecha.getUTCDate()}/${fecha.getUTCMonth() + 1}`, { tam: 11, ancla: "start", color: GRIS });
  }
  c += texto(20, 30, "Carta Gantt: plan (barra clara) y ejecución real (barra oscura)", { tam: 15, peso: "bold", ancla: "start" });
  tareas.forEach(([nombre, pi, pf, real, esFase, proyectado], i) => {
    const y = y0 + i * fila;
    c += texto(esFase ? 20 : 34, y + 14, nombre, { tam: esFase ? 13 : 12, peso: esFase ? "bold" : "normal", ancla: "start", color: esFase ? VERDE : TINTA });
    if (esFase) return;
    const px = xIni + aDia(pi) * colW, pw = (aDia(pf) - aDia(pi) + 1) * colW;
    c += `<rect x="${px}" y="${y + 1}" width="${pw}" height="9" rx="2" fill="#B9D8CA"/>`;
    const rx = xIni + aDia(real[0]) * colW, rw = (aDia(real[1]) - aDia(real[0]) + 1) * colW;
    c += `<rect x="${rx}" y="${y + 11}" width="${rw}" height="9" rx="2" fill="${proyectado ? AMBAR : VERDE}"/>`;
  });
  const hoy = xIni + aDia("15-11") * colW + colW / 2;
  c += `<line x1="${hoy}" y1="${y0 - 8}" x2="${hoy}" y2="${y0 + tareas.length * fila}" stroke="#C0392B" stroke-width="2" stroke-dasharray="5 3"/>`;
  c += texto(hoy + 4, y0 + tareas.length * fila + 16, "15 nov: corte de este informe", { tam: 11, ancla: "start", color: "#C0392B" });
  const ly = alto - 18;
  c += `<rect x="20" y="${ly - 10}" width="24" height="9" fill="#B9D8CA"/>` + texto(50, ly - 2, "Plan", { tam: 12, ancla: "start" });
  c += `<rect x="100" y="${ly - 10}" width="24" height="9" fill="${VERDE}"/>` + texto(130, ly - 2, "Real", { tam: 12, ancla: "start" });
  c += `<rect x="180" y="${ly - 10}" width="24" height="9" fill="${AMBAR}"/>` + texto(210, ly - 2, "En curso o programado", { tam: 12, ancla: "start" });
  guardar("06-carta-gantt", svg(ancho, alto, c));
})();

// ---------------------------------------------------------------- arquitectura
(function arquitectura() {
  let c = "";
  c += `<rect x="20" y="20" width="560" height="380" rx="14" fill="#F4F6F5" stroke="${LINEA}"/>` + texto(300, 46, "Teléfono Android (funciona sin señal)", { tam: 15, peso: "bold" });
  const capas = [["Pantallas (Jetpack Compose)", "chofer y administrador"], ["Modelos de vista (MVVM)", "estado y validaciones"], ["Repositorios", "reglas del negocio"], ["Room / SQLite", "fuente de datos del chofer"]];
  capas.forEach(([a, b], i) => {
    c += caja(60, 64 + i * 80, 300, 60, [a, b], { titulo: true, tam: 13 });
    if (i < 3) c += linea(210, 124 + i * 80, 210, 142 + i * 80);
  });
  c += caja(400, 304, 160, 60, ["WorkManager", "sincronización"], { titulo: true, tam: 13, relleno: "#FFF6DD", borde: AMBAR });
  c += linea(360, 334, 398, 334);
  c += caja(400, 144, 160, 60, ["GPS del teléfono", "sin internet"], { titulo: true, tam: 13 });
  c += linea(400, 174, 362, 254 - 70);
  c += `<rect x="640" y="20" width="300" height="380" rx="14" fill="${FONDO_VERDE}" stroke="${VERDE}"/>` + texto(790, 46, "Supabase (nube)", { tam: 15, peso: "bold" });
  const nube = [["PostgreSQL", "tablas con RLS y triggers"], ["Auth", "correo y contraseña"], ["Storage", "fotos y firmas"], ["Realtime", "lista en vivo del admin"]];
  nube.forEach(([a, b], i) => { c += caja(670, 64 + i * 80, 240, 60, [a, b], { titulo: true, tam: 13 }); });
  c += linea(560, 334, 668, 104);
  c += texto(615, 205, "HTTPS", { tam: 12, color: GRIS });
  guardar("07-arquitectura", svg(960, 420, c));
})();
