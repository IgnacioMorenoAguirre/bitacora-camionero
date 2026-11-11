-- =====================================================================
--  Bitácora del Camionero — Esquema de base de datos (PostgreSQL / Supabase)
--  Ejecutar completo en: Supabase > SQL Editor > New query > Run
-- =====================================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
do $$ begin
  create type rol_usuario      as enum ('chofer', 'admin');
  create type estado_viaje     as enum ('en_curso', 'finalizado', 'cancelado');
  create type estado_validacion as enum ('pendiente', 'aprobado', 'observado');
  create type tipo_evento      as enum ('inicio', 'parada', 'carga', 'descarga', 'fin');
  create type tipo_gasto       as enum ('combustible', 'peaje', 'alimentacion', 'mantencion', 'otro');
  create type tipo_novedad     as enum ('incidente', 'falla_mecanica', 'retraso', 'accidente', 'clima', 'otro');
  create type gravedad_novedad as enum ('baja', 'media', 'alta');
exception when duplicate_object then null; end $$;

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
  patente          text not null unique check (patente ~ '^[A-Z0-9]{4,8}$'),
  marca            text not null,
  modelo           text not null,
  anio             int check (anio between 1980 and 2100),
  rendimiento_km_l numeric(5,2) not null default 3.0 check (rendimiento_km_l > 0),
  activo           boolean not null default true,
  creado_en        timestamptz not null default now(),
  actualizado_en   timestamptz not null default now()
);

-- Los IDs de viajes, eventos, gastos y novedades se generan en el celular (UUID)
-- para poder crearlos sin conexión y sincronizarlos sin duplicados (upsert por id).
create table if not exists viajes (
  id                     uuid primary key,
  chofer_id              uuid not null references perfiles(id),
  camion_id              uuid references camiones(id),
  cliente                text not null,
  origen                 text not null,
  destino                text not null,
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

create table if not exists eventos (
  id              uuid primary key,
  viaje_id        uuid not null references viajes(id) on delete cascade,
  tipo            tipo_evento not null,
  fecha_hora      timestamptz not null,
  latitud         double precision check (latitud between -90 and 90),
  longitud        double precision check (longitud between -180 and 180),
  direccion       text,
  km              int check (km >= 0),
  nota            text,
  eliminado       boolean not null default false,
  creado_en       timestamptz not null default now(),
  actualizado_en  timestamptz not null default now()
);

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

create index if not exists idx_viajes_chofer      on viajes(chofer_id, fecha_salida desc);
create index if not exists idx_viajes_actualizado on viajes(actualizado_en);
create index if not exists idx_eventos_viaje      on eventos(viaje_id, fecha_hora);
create index if not exists idx_gastos_viaje       on gastos(viaje_id);
create index if not exists idx_novedades_viaje    on novedades(viaje_id);

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
  foreach t in array array['perfiles','camiones','viajes','eventos','gastos','novedades'] loop
    execute format('drop trigger if exists trg_%1$s_actualizado on %1$s', t);
    execute format('create trigger trg_%1$s_actualizado before insert or update on %1$s
                    for each row execute function tocar_actualizado()', t);
  end loop;
end $$;

-- Crea el perfil automáticamente al registrarse (siempre como chofer)
create or replace function crear_perfil() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into perfiles (id, nombre_completo)
  values (new.id, coalesce(new.raw_user_meta_data->>'nombre_completo', 'Sin nombre'));
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

-- Un chofer no puede cambiarse el rol ni validar sus propios viajes
create or replace function proteger_campos() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  -- Solo aplica a usuarios de la app; el SQL Editor (rol postgres) queda libre
  if auth.role() = 'authenticated' and not es_admin() then
    if tg_table_name = 'perfiles' then
      new.rol := old.rol;
      new.activo := old.activo;
    elsif tg_table_name = 'viajes' then
      if tg_op = 'INSERT' then
        new.validacion := 'pendiente';
        new.validado_por := null;
        new.comentario_validacion := null;
      else
        new.validacion := old.validacion;
        new.validado_por := old.validado_por;
        new.comentario_validacion := old.comentario_validacion;
        new.chofer_id := old.chofer_id;
      end if;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_perfiles_proteger on perfiles;
create trigger trg_perfiles_proteger before update on perfiles
  for each row execute function proteger_campos();
drop trigger if exists trg_viajes_proteger on viajes;
create trigger trg_viajes_proteger before insert or update on viajes
  for each row execute function proteger_campos();

-- ---------------------------------------------------------------------
-- Seguridad a nivel de fila (RLS)
-- ---------------------------------------------------------------------
alter table perfiles  enable row level security;
alter table camiones  enable row level security;
alter table viajes    enable row level security;
alter table eventos   enable row level security;
alter table gastos    enable row level security;
alter table novedades enable row level security;

drop policy if exists perfiles_select on perfiles;
create policy perfiles_select on perfiles for select using (id = auth.uid() or es_admin());
drop policy if exists perfiles_update on perfiles;
create policy perfiles_update on perfiles for update using (id = auth.uid() or es_admin());

drop policy if exists camiones_select on camiones;
create policy camiones_select on camiones for select using (auth.role() = 'authenticated');
drop policy if exists camiones_admin on camiones;
create policy camiones_admin on camiones for all using (es_admin()) with check (es_admin());

drop policy if exists viajes_select on viajes;
create policy viajes_select on viajes for select using (chofer_id = auth.uid() or es_admin());
drop policy if exists viajes_insert on viajes;
create policy viajes_insert on viajes for insert with check (chofer_id = auth.uid());
drop policy if exists viajes_update on viajes;
create policy viajes_update on viajes for update using (chofer_id = auth.uid() or es_admin());

do $$
declare t text;
begin
  foreach t in array array['eventos','gastos','novedades'] loop
    execute format('drop policy if exists %1$s_select on %1$s', t);
    execute format('create policy %1$s_select on %1$s for select using (es_mi_viaje(viaje_id) or es_admin())', t);
    execute format('drop policy if exists %1$s_insert on %1$s', t);
    execute format('create policy %1$s_insert on %1$s for insert with check (es_mi_viaje(viaje_id))', t);
    execute format('drop policy if exists %1$s_update on %1$s', t);
    execute format('create policy %1$s_update on %1$s for update using (es_mi_viaje(viaje_id))', t);
  end loop;
end $$;
-- Nota: no hay políticas DELETE. Los registros se "eliminan" con eliminado = true,
-- así el borrado también se sincroniza a otros dispositivos y queda trazabilidad.

-- ---------------------------------------------------------------------
-- Vista para reportes del administrador
-- ---------------------------------------------------------------------
create or replace view resumen_viajes with (security_invoker = true) as
select
  v.id, v.chofer_id, p.nombre_completo as chofer, c.patente,
  v.cliente, v.origen, v.destino, v.fecha_salida, v.fecha_llegada,
  v.estado, v.validacion, v.precio,
  (v.km_final - v.km_inicial)                                         as km_recorridos,
  coalesce(sum(g.monto) filter (where g.tipo = 'combustible'), 0)     as gasto_combustible,
  coalesce(sum(g.monto) filter (where g.tipo = 'peaje'), 0)           as gasto_peajes,
  coalesce(sum(g.monto), 0)                                           as gasto_total,
  v.precio - coalesce(sum(g.monto), 0)                                as margen
from viajes v
join perfiles p      on p.id = v.chofer_id
left join camiones c on c.id = v.camion_id
left join gastos g   on g.viaje_id = v.id and not g.eliminado
where not v.eliminado
group by v.id, p.nombre_completo, c.patente;

-- ---------------------------------------------------------------------
-- Para convertir un usuario en administrador (ejecutar a mano):
--   update perfiles set rol = 'admin' where id = (select id from auth.users where email = 'correo@ejemplo.cl');
-- ---------------------------------------------------------------------
