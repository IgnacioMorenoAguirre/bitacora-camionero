# Guion de la demo

Duración aproximada: 8 a 10 minutos. Se necesitan dos teléfonos (o un teléfono y el emulador): uno con la cuenta de chofer y otro con la de administrador. Si hay un solo equipo, se cierra sesión y se entra con la otra cuenta en el paso 9.

## Antes de empezar

- Instalar `BitacoraCamionero-release.apk` en los dos equipos.
- Cuentas de prueba: el correo y la contraseña de cada una están en `pruebas-cuentas.local.txt`, que no se sube a GitHub. Pedírselo a Ignacio.
- Entrar con las dos cuentas **con internet** antes de la presentación. Así queda la sesión guardada y los camiones descargados.
- Revisar que el teléfono del chofer tenga la ubicación activada y la app tenga permiso de ubicación.
- Dejar abierta en el teléfono del administrador la pestaña **Viajes**.

## Recorrido

| # | Quién | Qué se hace | Qué debe verse |
|---|---|---|---|
| 1 | Chofer | Activar modo avión. Abrir la app. | Entra directo al Inicio, sin pedir contraseña. Desde el primer registro, arriba dice "Sin señal. N registros se enviarán solos." |
| 2 | Chofer | **Crear viaje**. Paso 1: cliente, origen, destino, ruta (ej. 5 SUR), carga, peso y precio. | Si se deja un campo vacío o el peso en letras, aparece el error bajo el campo. |
| 3 | Chofer | Paso 2: elegir camión y escribir el kilometraje al salir. | Los camiones aparecen aunque no haya señal. |
| 4 | Chofer | Paso 3: marcar la revisión del camión y **Iniciar viaje**. | Se abre la hoja de ruta con el letrero verde de la ruta y el primer hito "Inicio en ...". |
| 5 | Chofer | **Registrar parada** → elegir descanso. Luego terminar el descanso. | Aparece el hito con la hora. El contador de manejo continuo se reinicia si el descanso dura 30 minutos o más. |
| 6 | Chofer | **Combustible**: litros, monto y km. Luego **Peaje** y una **Novedad** (ej. control de carretera). | Cada registro suma un hito en el camino con su kilómetro. |
| 7 | Chofer | **Finalizar**: km al llegar, quién recibe, **Tomar foto de la guía** o **Pedir firma**. | Si el km es menor que el inicial, la app no deja seguir. Al finalizar se abre el resumen: km, horas, gastos, ganancia y "Firma de ... Por enviar". |
| 8 | Chofer | Quitar el modo avión. | En unos segundos el indicador cambia a "Todo enviado" y el respaldo a "Enviada". |
| 9 | Admin | Mirar la lista de viajes. | El viaje aparece solo, marcado "Por revisar". |
| 10 | Admin | Abrir el viaje. | Se ve el camino con todos los hitos, los gastos, el resumen y la foto o firma de la entrega. |
| 11 | Admin | Escribir una observación (ej. "Falta la región en el destino") y tocar **Observar**. | Desaparecen los botones y dice "Esperando la corrección del chofer". |
| 12 | Chofer | **Mis viajes** → abrir el viaje → **Corregir viaje**, cambiar el destino y guardar. | El viaje vuelve a "Pendiente de revisión". |
| 13 | Admin | Abrir otra vez el viaje y tocar **Aprobar**. | Queda "Bitácora aprobada". En el teléfono del chofer el viaje queda de solo lectura. |
| 14 | Admin | Pestaña **Reportes**. | Totales del mes, aprobados/por revisar/observados y barras por chofer y por camión. Cambiar entre Km y Ganancia. |
| 15 | Admin | Pestaña **Camiones**: agregar uno con patente inválida (ej. "ABC12"). | Bajo el campo aparece "Escribe una patente válida, como BBBB12 o AB1234." |

## Puntos para comentar

- Todo lo que hace el chofer se guarda primero en el teléfono (Room). Por eso funciona en zonas sin cobertura, que es lo normal en las rutas de Chile.
- La sincronización la hace WorkManager: aunque se cierre la app, los datos se envían cuando vuelve la señal.
- Supabase revisa los permisos en la base de datos (seguridad por fila): un chofer solo ve y cambia sus viajes, y una bitácora aprobada ya no se puede modificar, ni siquiera llamando directo al servidor.
- La sesión se guarda cifrada con una llave del Android Keystore.
- El límite de 5 horas de manejo continuo avisa 30 minutos antes.

## Si algo falla

- **No llega "Todo enviado"**: en Perfil → **Sincronizar ahora**. Revisar que el teléfono tenga internet de verdad (abrir una página web).
- **El viaje no aparece en el admin**: la lista se actualiza sola cuando el chofer sincroniza. Si no llega, cambiar a otra pestaña y volver a **Viajes**, o revisar que el chofer diga "Todo enviado".
- **No hay internet en la sala**: hacer la parte del chofer igual (todo funciona en modo avión) y mostrar el lado del administrador con las capturas de `docs/capturas`.
- **La cámara no abre en el emulador**: usar **Pedir firma**.
