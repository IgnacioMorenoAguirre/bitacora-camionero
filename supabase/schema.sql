-- =====================================================================
--  Bitácora del Camionero — Esquema de base de datos (PostgreSQL / Supabase)
--  Ejecutar completo en: Supabase > SQL Editor > New query > Run.
--  Se puede volver a ejecutar sin perder datos (usa IF NOT EXISTS / OR REPLACE).
-- =====================================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
do $$ begin create type rol_usuario as enum ('chofer', 'admin'); exception when duplicate_object then null; end $$;
do $$ begin create type estado_viaje as enum ('en_curso', 'finalizado', 'cancelado'); exception when duplicate_object then null; end $$;
do $$ begin create type estado_validacion as enum ('pendiente', 'aprobado', 'observado'); exception when duplicate_object then null; end $$;
do $$ begin create type tipo_evento as enum ('inicio', 'parada', 'descanso', 'carga', 'descarga', 'entrega', 'fin'); exception when duplicate_object then null; end $$;
do $$ begin create type tipo_gasto as enum ('combustible', 'peaje', 'alimentacion', 'mantencion', 'otro'); exception when duplicate_object then null; end $$;
do $$ begin create type tipo_novedad as enum ('incidente', 'falla_mecanica', 'retraso', 'accidente', 'clima', 'otro'); exception when duplicate_object then null; end $$;
do $$ begin create type gravedad_novedad as enum ('baja', 'media', 'alta'); exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------
-- Tablas
-- ---------------------------------------------------------------------

-- Perfil de cada usuario (1:1 con auth.users, que guarda correo y contraseña cifrada)
create table if not exists perfiles (
  id               uuid primary key references auth.users(id) on delete cascade,
  nombre_completo  text not null check (char_length(nombre_completo) between 3 and 120),
  rut              text unique,
  telefono         text,
  rol              rol_usuario not null default 'chofer',
  activo           boolean not null default true,
  creado_en        timestamptz not null default now(),
  actualizado_en   timestamptz not null default now()
);

create table if not exists camiones (
  id               uuid primary key default gen_random_uuid(),
  patente          text not null unique check (patente ~ '^[A-Z]{4}[0-9]{2}$|^[A-Z]{2}[0-9]{4}$'),
  marca            text not null,
  modelo           text not null,
  anio             int check (anio between 1980 and 2100),
  rendimiento_km_l numeric(5,2) not null default 3.0 check (rendimiento_km_l > 0),
  activo           boolean not null default true,
  creado_en        timestamptz not null default now(),
  actualizado_en   timestamptz not null default now()
);

-- Los IDs de viajes y sus registros se generan en el celular (UUID) para poder crearlos
-- sin conexión y sincronizarlos sin duplicados (upsert por id).
create table if not exists viajes (
  id                     uuid primary key,
  chofer_id              uuid not null references perfiles(id),
  camion_id              uuid references camiones(id),
  cliente                text not null,
  origen                 text not null,
  destino                text not null,
  ruta                   text,
  tipo_carga             text not null,
  peso_kg                numeric(10,2) not null check (peso_kg > 0),
  requiere_refrigeracion boolean not null default false,
  precio                 numeric(12,0) not null check (precio >= 0),
  fecha_salida           timestamptz not null,
  fecha_llegada          timestamptz,
  km_inicial             int check (km_inicial >= 0),
  km_final               int,
  estado                 estado_viaje not null default 'en_curso',
  validacion             estado_validacion not null default 'pendiente',
  comentario_validacion  text,
  validado_por           uuid references perfiles(id),
  observaciones          text,
  eliminado              boolean not null default false,
  creado_en              timestamptz not null default now(),
  actualizado_en         timestamptz not null default now(),
  constraint km_coherente check (km_final is null or km_inicial is null or km_final >= km_inicial),
  constraint fechas_coherentes check (fecha_llegada is null or fecha_llegada >= fecha_salida)
);
alter table viajes add column if not exists ruta text;

create table if not exists revisiones (
  id              uuid primary key,
  viaje_id        uuid not null unique references viajes(id) on delete cascade,
  neumaticos      boolean not null,
  luces           boolean not null,
  frenos          boolean not null,
  niveles         boolean not null,
  documentos      boolean not null,
  comentario      text,
  eliminado       boolean not null default false,
  creado_en       timestamptz not null default now(),
  actualizado_en  timestamptz not null default now()
);

create table if not exists eventos (
  id              uuid primary key,
  viaje_id        uuid not null references viajes(id) on delete cascade,
  tipo            tipo_evento not null,
  fecha_hora      timestamptz not null,
  fecha_fin       timestamptz,
  latitud         double precision check (latitud between -90 and 90),
  longitud        double precision check (longitud between -180 and 180),
  direccion       text,
  km              int check (km >= 0),
  nota            text,
  eliminado       boolean not null default false,
  creado_en       timestamptz not null default now(),
  actualizado_en  timestamptz not null default now(),
  constraint evento_fechas check (fecha_fin is null or fecha_fin >= fecha_hora)
);
alter table eventos add column if not exists fecha_fin timestamptz;

create table if not exists gastos (
  id              uuid primary key,
  viaje_id        uuid not null references viajes(id) on delete cascade,
  tipo            tipo_gasto not null,
  monto           numeric(12,0) not null check (monto >= 0),
  litros          numeric(8,2) check (litros > 0),
  fecha_hora      timestamptz not null,
  descripcion     text,
  eliminado       boolean not null default false,
  creado_en       timestamptz not null default now(),
  actualizado_en  timestamptz not null default now()
);

create table if not exists novedades (
  id              uuid primary key,
  viaje_id        uuid not null references viajes(id) on delete cascade,
  tipo            tipo_novedad not null,
  gravedad        gravedad_novedad not null default 'baja',
  descripcion     text not null,
  fecha_hora      timestamptz not null,
  latitud         double precision,
  longitud        double precision,
  eliminado       boolean not null default false,
  creado_en       timestamptz not null default now(),
  actualizado_en  timestamptz not null default now()
);

create table if not exists adjuntos (
  id               uuid primary key,
  viaje_id         uuid not null references viajes(id) on delete cascade,
  tipo             text not null check (tipo in ('foto', 'firma')),
  ruta_remota      text,
  nombre_receptor  text,
  fecha_hora       timestamptz not null,
  eliminado        boolean not null default false,
  creado_en        timestamptz not null default now(),
  actualizado_en   timestamptz not null default now()
);

create index if not exists idx_viajes_chofer      on viajes(chofer_id, fecha_salida desc);
create index if not exists idx_viajes_actualizado on viajes(actualizado_en);
create index if not exists idx_eventos_viaje      on eventos(viaje_id, fecha_hora);
create index if not exists idx_eventos_act        on eventos(actualizado_en);
create index if not exists idx_gastos_viaje       on gastos(viaje_id);
create index if not exists idx_gastos_act         on gastos(actualizado_en);
create index if not exists idx_novedades_viaje    on novedades(viaje_id);
create index if not exists idx_novedades_act      on novedades(actualizado_en);
create index if not exists idx_adjuntos_viaje     on adjuntos(viaje_id);
create index if not exists idx_revisiones_act     on revisiones(actualizado_en);

-- ---------------------------------------------------------------------
-- Funciones y triggers
-- ---------------------------------------------------------------------

-- actualizado_en lo fija el servidor: es la marca que usa la app para bajar cambios
create or replace function tocar_actualizado() returns trigger
language plpgsql as $$
begin
  new.actualizado_en := now();
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['perfiles','camiones','viajes','revisiones','eventos','gastos','novedades','adjuntos'] loop
    execute format('drop trigger if exists trg_%1$s_actualizado on %1$s', t);
    execute format('create trigger trg_%1$s_actualizado before insert or update on %1$s
                    for each row execute function tocar_actualizado()', t);
  end loop;
end $$;

-- Crea el perfil automáticamente al registrarse (siempre como chofer)
create or replace function crear_perfil() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.perfiles (id, nombre_completo)
  values (new.id, coalesce(nullif(new.raw_user_meta_data->>'nombre_completo', ''), 'Sin nombre'));
  return new;
end $$;

drop trigger if exists trg_crear_perfil on auth.users;
create trigger trg_crear_perfil after insert on auth.users
  for each row execute function crear_perfil();

create or replace function es_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from perfiles where id = auth.uid() and rol = 'admin' and activo);
$$;

create or replace function es_mi_viaje(v uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from viajes where id = v and chofer_id = auth.uid());
$$;

-- Un chofer que el administrador desactivó deja de guardar, aunque su teléfono conserve la sesión.
create or replace function soy_activo() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from perfiles where id = auth.uid() and activo);
$$;

create or replace function viaje_aprobado(v uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from viajes where id = v and validacion = 'aprobado');
$$;

-- Reglas que no dependen de la app:
--  * un chofer no se cambia el rol ni se activa/desactiva;
--  * un chofer no aprueba sus viajes ni edita un viaje aprobado (VIAJE_APROBADO);
--  * si el chofer corrige un viaje observado, vuelve a "pendiente";
--  * el administrador solo valida viajes finalizados (SOLO_FINALIZADOS).
-- Solo aplica a usuarios de la app; el SQL Editor (rol postgres) queda libre.
create or replace function proteger_campos() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if auth.role() is distinct from 'authenticated' then
    return new;
  end if;

  if tg_table_name = 'perfiles' then
    if not es_admin() then
      new.rol := old.rol;
      new.activo := old.activo;
    end if;
    return new;
  end if;

  -- viajes
  if es_admin() then
    if tg_op = 'UPDATE' and new.validacion is distinct from old.validacion then
      if new.estado <> 'finalizado' then
        raise exception 'SOLO_FINALIZADOS' using errcode = 'P0001';
      end if;
      new.validado_por := auth.uid();
    end if;
    return new;
  end if;

  if tg_op = 'INSERT' then
    new.validacion := 'pendiente';
    new.validado_por := null;
    new.comentario_validacion := null;
    return new;
  end if;

  if old.validacion = 'aprobado' then
    raise exception 'VIAJE_APROBADO' using errcode = 'P0001';
  end if;
  new.chofer_id := old.chofer_id;
  new.validado_por := old.validado_por;
  if old.validacion = 'observado' then
    new.validacion := 'pendiente';
    new.comentario_validacion := old.comentario_validacion;
  else
    new.validacion := old.validacion;
    new.comentario_validacion := old.comentario_validacion;
  end if;
  return new;
end $$;

drop trigger if exists trg_perfiles_proteger on perfiles;
create trigger trg_perfiles_proteger before update on perfiles
  for each row execute function proteger_campos();
drop trigger if exists trg_viajes_proteger on viajes;
create trigger trg_viajes_proteger before insert or update on viajes
  for each row execute function proteger_campos();

-- Un viaje aprobado queda cerrado también para sus registros hijos: un chofer no puede cambiar ni
-- borrar revisiones, eventos, gastos, novedades ni adjuntos (VIAJE_APROBADO, igual que
-- proteger_campos para el viaje). Dos excepciones:
--  * una fila nueva (su id todavía no existe) sí entra: la app no crea registros en un viaje
--    finalizado, así que una fila nueva que llega tarde se anotó durante el viaje y solo se demoró
--    por falta de señal (p. ej. la foto de la entrega);
--  * reenviar una fila idéntica: la app reintenta con upsert (y el INSERT ... ON CONFLICT dispara
--    este trigger antes de detectar el conflicto), así que solo frena cuando algún dato realmente
--    cambia; creado_en y actualizado_en no cuentan.
create or replace function proteger_hijos_aprobados() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  ignorar  text[] := array['creado_en', 'actualizado_en'];
  previo   jsonb;
  viajes_a uuid[];
begin
  if auth.role() is distinct from 'authenticated' or es_admin() then
    if tg_op = 'DELETE' then return old; end if;
    return new;
  end if;

  if tg_op = 'DELETE' then
    viajes_a := array[old.viaje_id];
  elsif tg_op = 'UPDATE' then
    if (to_jsonb(old) - ignorar) is not distinct from (to_jsonb(new) - ignorar) then
      return new;
    end if;
    viajes_a := array[old.viaje_id, new.viaje_id];
  else
    -- INSERT: una fila nueva entra siempre; si ya existe con los mismos datos es un reenvío (upsert).
    execute format('select to_jsonb(t) - $2 from public.%I t where t.id = $1', tg_table_name)
      into previo using new.id, ignorar;
    if previo is null or previo is not distinct from (to_jsonb(new) - ignorar) then
      return new;
    end if;
    viajes_a := array[new.viaje_id];
  end if;

  if exists (select 1 from viajes v where v.id = any (viajes_a) and v.validacion = 'aprobado') then
    raise exception 'VIAJE_APROBADO' using errcode = 'P0001';
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['revisiones','eventos','gastos','novedades','adjuntos'] loop
    execute format('drop trigger if exists trg_%1$s_proteger on %1$s', t);
    execute format('create trigger trg_%1$s_proteger before insert or update or delete on %1$s
                    for each row execute function proteger_hijos_aprobados()', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------
-- Seguridad a nivel de fila (RLS)
-- ---------------------------------------------------------------------
alter table perfiles   enable row level security;
alter table camiones   enable row level security;
alter table viajes     enable row level security;
alter table revisiones enable row level security;
alter table eventos    enable row level security;
alter table gastos     enable row level security;
alter table novedades  enable row level security;
alter table adjuntos   enable row level security;

drop policy if exists perfiles_select on perfiles;
create policy perfiles_select on perfiles for select to authenticated using (id = auth.uid() or es_admin());
drop policy if exists perfiles_update on perfiles;
create policy perfiles_update on perfiles for update to authenticated using (id = auth.uid() or es_admin());

drop policy if exists camiones_select on camiones;
create policy camiones_select on camiones for select to authenticated using (true);
drop policy if exists camiones_admin on camiones;
create policy camiones_admin on camiones for all to authenticated using (es_admin()) with check (es_admin());

drop policy if exists viajes_select on viajes;
create policy viajes_select on viajes for select to authenticated using (chofer_id = auth.uid() or es_admin());
drop policy if exists viajes_insert on viajes;
create policy viajes_insert on viajes for insert to authenticated with check (chofer_id = auth.uid() and soy_activo());
drop policy if exists viajes_update on viajes;
create policy viajes_update on viajes for update to authenticated using ((chofer_id = auth.uid() and soy_activo()) or es_admin());

do $$
declare t text;
begin
  foreach t in array array['revisiones','eventos','gastos','novedades','adjuntos'] loop
    execute format('drop policy if exists %1$s_select on %1$s', t);
    execute format('create policy %1$s_select on %1$s for select to authenticated using (es_mi_viaje(viaje_id) or es_admin())', t);
    execute format('drop policy if exists %1$s_insert on %1$s', t);
    execute format('create policy %1$s_insert on %1$s for insert to authenticated with check (es_mi_viaje(viaje_id) and soy_activo())', t);
    execute format('drop policy if exists %1$s_update on %1$s', t);
    execute format('create policy %1$s_update on %1$s for update to authenticated using (es_mi_viaje(viaje_id) and soy_activo())', t);
  end loop;
end $$;
-- No hay políticas DELETE: los registros se "eliminan" con eliminado = true,
-- así el borrado también se sincroniza y queda trazabilidad.

-- Permisos de la API (el proyecto no expone tablas automáticamente; la RLS de arriba filtra las filas)
grant usage on schema public to anon, authenticated;
grant select, insert, update on perfiles, camiones, viajes, revisiones, eventos, gastos, novedades, adjuntos to authenticated;
grant delete on camiones to authenticated;

-- ---------------------------------------------------------------------
-- Vista para reportes del administrador
-- ---------------------------------------------------------------------
drop view if exists resumen_viajes;
create view resumen_viajes with (security_invoker = true) as
select
  v.id, v.chofer_id, p.nombre_completo as chofer, c.patente,
  v.cliente, v.origen, v.destino, v.fecha_salida, v.fecha_llegada,
  v.estado, v.validacion, v.precio,
  (v.km_final - v.km_inicial)                                              as km_recorridos,
  coalesce(sum(g.monto) filter (where g.tipo = 'combustible'), 0)          as gasto_combustible,
  coalesce(sum(g.monto) filter (where g.tipo = 'peaje'), 0)                as gasto_peajes,
  coalesce(sum(g.monto), 0)                                                as gasto_total,
  coalesce(sum(g.litros) filter (where g.tipo = 'combustible'), 0)         as litros,
  v.precio - coalesce(sum(g.monto), 0)                                     as margen
from viajes v
join perfiles p      on p.id = v.chofer_id
left join camiones c on c.id = v.camion_id
left join gastos g   on g.viaje_id = v.id and not g.eliminado
where not v.eliminado
group by v.id, p.nombre_completo, c.patente;

grant select on resumen_viajes to authenticated;

-- ---------------------------------------------------------------------
-- Almacenamiento de fotos y firmas (bucket privado "adjuntos", rutas viaje_id/archivo)
--  * solo JPEG o PNG de hasta 5 MB (la app sube fotos reducidas a 1600 px y firmas PNG);
--  * en la carpeta de un viaje aprobado el chofer puede subir un archivo nuevo (la foto que llegó
--    tarde, igual que proteger_hijos_aprobados), pero no reemplazar uno existente; borrar no puede
--    nunca (no hay política DELETE).
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('adjuntos', 'adjuntos', false, 5242880, array['image/jpeg', 'image/png'])
on conflict (id) do update
  set file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists adjuntos_subir on storage.objects;
create policy adjuntos_subir on storage.objects for insert to authenticated
  with check (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo());
drop policy if exists adjuntos_actualizar on storage.objects;
create policy adjuntos_actualizar on storage.objects for update to authenticated
  using (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo()
         and not viaje_aprobado(((storage.foldername(name))[1])::uuid))
  with check (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo()
              and not viaje_aprobado(((storage.foldername(name))[1])::uuid));
drop policy if exists adjuntos_leer on storage.objects;
create policy adjuntos_leer on storage.objects for select to authenticated
  using (bucket_id = 'adjuntos' and (es_mi_viaje(((storage.foldername(name))[1])::uuid) or es_admin()));

-- ---------------------------------------------------------------------
-- Actualización en vivo para la lista del administrador
-- ---------------------------------------------------------------------
do $$ begin
  alter publication supabase_realtime add table viajes;
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------
-- Datos de ejemplo
-- ---------------------------------------------------------------------
insert into camiones (patente, marca, modelo, anio, rendimiento_km_l) values
  ('KFZT21', 'Volvo', 'FH 460', 2021, 3.0),
  ('HJRP54', 'Scania', 'R 450', 2019, 3.2),
  ('BBCL87', 'Mercedes-Benz', 'Actros 2645', 2018, 2.9)
on conflict (patente) do nothing;

-- ---------------------------------------------------------------------
-- Para convertir un usuario en administrador (ejecutar a mano, cambiando el correo):
--   update perfiles set rol = 'admin'
--   where id = (select id from auth.users where email = 'correo@ejemplo.cl');
-- ---------------------------------------------------------------------
