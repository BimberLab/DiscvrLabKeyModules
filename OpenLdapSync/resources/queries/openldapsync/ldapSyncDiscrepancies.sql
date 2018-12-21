SELECT
CASE
  WHEN u.userid IS NULL THEN 'LDAP Synced, but missing from LABKEY'
  ELSE 'Present in LABKEY, but was not synced from LDAP'
END as status,
l.rowid,
l.provider,
l.sourceId,
l.labkeyId,
l.created,
u.userid,
u.type,
u.displayName,
u.email

FROM openldapsync.ldapSyncMap l
FULL JOIN core.UsersAndGroups u ON (u.userId = l.labkeyId)
WHERE (l.rowid is null AND u.type != 'g') or (u.userid is null)