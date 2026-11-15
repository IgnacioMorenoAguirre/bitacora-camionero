-- =====================================================================
--  Correcciones de la revisión final (15 nov 2026)
--  Ejecutar UNA vez en: Supabase > SQL Editor > New query > Run.
--  Solo trae lo que cambió; supabase/schema.sql ya incluye todo esto.
--  Se puede volver a ejecutar sin problema (OR REPLACE / DROP IF EXISTS).
-- =====================================================================

begin;

-- 1. Un viaje aprobado acepta registros hijos NUEVOS que llegan tarde (p. ej. la foto de la entrega
--    que no alcanzó a subir antes de la aprobación). Cambiar o borrar lo aprobado sigue bloqueado.
--    Los triggers trg_*_proteger ya existen y usan esta función: basta con reemplazarla.
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

-- 2. Funciones de apoyo para las políticas.
create or replace function soy_activo() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from perfiles where id = auth.uid() and activo);
$$;

create or replace function viaje_aprobado(v uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from viajes where id = v and validacion = 'aprobado');
$$;

-- 3. Un chofer desactivado deja de guardar viajes y registros (aunque conserve la sesión).
drop policy if exists viajes_insert on viajes;
create policy viajes_insert on viajes for insert to authenticated with check (chofer_id = auth.uid() and soy_activo());
drop policy if exists viajes_update on viajes;
create policy viajes_update on viajes for update to authenticated using ((chofer_id = auth.uid() and soy_activo()) or es_admin());

do $$
declare t text;
begin
  foreach t in array array['revisiones','eventos','gastos','novedades','adjuntos'] loop
    execute format('drop policy if exists %1$s_insert on %1$s', t);
    execute format('create policy %1$s_insert on %1$s for insert to authenticated with check (es_mi_viaje(viaje_id) and soy_activo())', t);
    execute format('drop policy if exists %1$s_update on %1$s', t);
    execute format('create policy %1$s_update on %1$s for update to authenticated using (es_mi_viaje(viaje_id) and soy_activo())', t);
  end loop;
end $$;

-- 4. Bucket "adjuntos": solo JPEG o PNG de hasta 5 MB.
update storage.buckets
set file_size_limit = 5242880, allowed_mime_types = array['image/jpeg', 'image/png']
where id = 'adjuntos';

-- 5. Storage: en la carpeta de un viaje aprobado se puede subir un archivo nuevo, pero no
--    reemplazar uno existente (no hay política DELETE, así que borrar sigue prohibido).
drop policy if exists adjuntos_subir on storage.objects;
create policy adjuntos_subir on storage.objects for insert to authenticated
  with check (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo());
drop policy if exists adjuntos_actualizar on storage.objects;
create policy adjuntos_actualizar on storage.objects for update to authenticated
  using (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo()
         and not viaje_aprobado(((storage.foldername(name))[1])::uuid))
  with check (bucket_id = 'adjuntos' and es_mi_viaje(((storage.foldername(name))[1])::uuid) and soy_activo()
              and not viaje_aprobado(((storage.foldername(name))[1])::uuid));

commit;

-- Comprobación (debe mostrar 5242880 y {image/jpeg,image/png}):
--   select id, file_size_limit, allowed_mime_types from storage.buckets where id = 'adjuntos';
