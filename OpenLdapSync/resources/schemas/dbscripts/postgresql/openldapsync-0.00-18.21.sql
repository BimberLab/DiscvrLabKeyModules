/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Create schema, tables, indexes, and constraints used for OpenLdapSync module here
-- All SQL VIEW definitions should be created in openldapsync-create.sql and dropped in openldapsync-drop.sql
CREATE SCHEMA openldapsync;

CREATE TABLE openldapsync.ldapSyncMap (
  rowid serial,
  provider varchar(1000),
  sourceId varchar(1000),
  labkeyId int,
  type char(1),

  created timestamp,
  container entityid
);

--this table was originally in the LDK module
CREATE FUNCTION openldapsync.handleUpgrade() RETURNS VOID AS $$
DECLARE
BEGIN
  IF EXISTS ( SELECT * FROM information_schema.tables WHERE table_schema = 'ldk' AND table_name = 'ldapSyncMap' )
  THEN
    INSERT INTO openldapsync.ldapSyncMap (provider, sourceId, labkeyId, type, created, container)
      SELECT provider, sourceId, labkeyId, type, created, (select entityid from core.containers WHERE name IS NULL) as container
      FROM ldk.ldapSyncMap;
  END IF;
END;
$$ LANGUAGE plpgsql;

SELECT openldapsync.handleUpgrade();

DROP FUNCTION openldapsync.handleUpgrade();

