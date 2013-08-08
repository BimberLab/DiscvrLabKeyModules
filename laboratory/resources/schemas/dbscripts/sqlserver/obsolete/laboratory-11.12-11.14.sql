/*
 * Copyright (c) 2011-2012 LabKey Corporation
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


--NOTE by b.bimber: i removed many NOT NULL from columns.
--  given no one else should have installed this module at this point it is just cleaner to do a full replace



ALTER TABLE laboratory.inventory
  add ratio float
ALTER TABLE laboratory.inventory
  drop column freezer
ALTER TABLE laboratory.inventory
  add freezer varchar(100)
ALTER TABLE laboratory.inventory
  add cell_number float
GO

INSERT INTO laboratory.sample_type VALUES ('BLCL');
INSERT INTO laboratory.sample_type VALUES ('DNA');
INSERT INTO laboratory.sample_type VALUES ('Whole Blood');