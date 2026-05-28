delete from marketbot.app_user_property property
using marketbot.app_user_property duplicate
where property.id > duplicate.id
  and property.user_id = duplicate.user_id
  and property.property_type = duplicate.property_type
  and property.property_name = duplicate.property_name;

do $$
declare constraint_name text;
begin
    for constraint_name in
        select con.conname
        from pg_constraint con
        join pg_class rel on rel.oid = con.conrelid
        join pg_namespace ns on ns.oid = rel.relnamespace
        where ns.nspname = 'marketbot'
          and rel.relname = 'app_user_property'
          and con.contype = 'u'
    loop
        execute format('alter table marketbot.app_user_property drop constraint if exists %I', constraint_name);
    end loop;
end $$;

alter table marketbot.app_user_property
    add constraint uk_app_user_property_user_type_name
        unique (user_id, property_type, property_name);

alter table marketbot.app_user_property
    drop constraint if exists app_user_property_property_type_check;

alter table marketbot.app_user_property
    add constraint app_user_property_property_type_check
        check (property_type in (
            'BOT',
            'WATCHLIST',
            'CRYPTO_COIN',
            'ALERT_SETTING',
            'DASHBOARD_SETTING',
            'CUSTOM'
        ));

alter table marketbot.app_user_property
    drop constraint if exists app_user_property_property_value_type_check;

alter table marketbot.app_user_property
    add constraint app_user_property_property_value_type_check
        check (property_value_type in (
            'TEXT',
            'SECRET',
            'SYMBOL'
        ));
