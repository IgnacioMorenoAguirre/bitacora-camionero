# Configuración de Supabase

Proyecto: `bitacora-camionero` (plan gratuito, región South America / São Paulo), creado el 13 de noviembre de 2026.

## Lo que ya está hecho

1. Se ejecutó [supabase/schema.sql](../supabase/schema.sql) en el SQL Editor. Crea las 8 tablas con seguridad por fila, los triggers, la vista `resumen_viajes`, el bucket privado `adjuntos`, la publicación en vivo de `viajes` y 3 camiones de ejemplo. El script se puede volver a ejecutar sin perder datos.
2. Authentication → Sign In / Providers: **Confirm email desactivado**. Así los usuarios entran apenas se registran (el correo gratuito de Supabase tiene un límite bajo de envíos).
3. Authentication → URL Configuration: se agregó la dirección **`bitacora://recuperar`**. El correo de recuperación de contraseña trae un enlace que abre la app en la pantalla para elegir una contraseña nueva.

Supabase no permite editar las plantillas de correo sin un servidor SMTP propio, por eso la recuperación usa enlace y no un código de 6 dígitos. Con el correo gratuito, los mensajes solo llegan a direcciones del equipo de la organización en Supabase; para la demo conviene usar un correo del dueño del proyecto o agregar a los compañeros al equipo.

## Conectar la app

La app lee la URL y la llave pública desde `local.properties` (archivo que no se sube a Git):

```
supabase.url=https://<referencia-del-proyecto>.supabase.co
supabase.anonKey=<llave anon public de Settings → API Keys>
```

La llave `anon` es pública por diseño: la seguridad la ponen las reglas de la base de datos. Nunca se usa la llave `service_role` en la app.

## Crear el primer administrador

Después de que la persona se registre en la app, en el SQL Editor:

```sql
update perfiles set rol = 'admin'
where id = (select id from auth.users where email = 'correo@ejemplo.cl');
```

## Verificación rápida

```sql
select count(*) from pg_tables where schemaname = 'public'
  and tablename in ('perfiles','camiones','viajes','revisiones','eventos','gastos','novedades','adjuntos');
-- 8
```
