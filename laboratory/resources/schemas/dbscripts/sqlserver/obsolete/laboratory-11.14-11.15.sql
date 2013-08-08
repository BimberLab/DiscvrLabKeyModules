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


ALTER TABLE laboratory.inventory
  add preparationmethod varchar(200)
ALTER TABLE laboratory.inventory
  add samplesubtype varchar(200)
ALTER TABLE laboratory.inventory
  add quantity varchar(200)
ALTER TABLE laboratory.inventory
  add quantity_units varchar(50)
ALTER TABLE laboratory.inventory
  drop column cell_number
GO

-- ----------------------------
-- Table structure for laboratory.cell_type
-- ----------------------------
IF OBJECT_ID('laboratory.cell_type','U') IS NOT NULL
DROP TABLE laboratory.cell_type
GO
CREATE TABLE laboratory.cell_type (
  type varchar(105) NOT NULL,

  CONSTRAINT PK_cell_type PRIMARY KEY (type)
)
;
