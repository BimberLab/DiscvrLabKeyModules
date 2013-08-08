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

alter table laboratory.samples
  add column cane2 varchar(100),
  add column box2 varchar(100),
  add column box_row2 varchar(100),
  add column box_column2 varchar(100)
;

update laboratory.samples set cane2 = cane;
update laboratory.samples set box2 = box;
update laboratory.samples set box_row2 = box_row;
update laboratory.samples set box_column2 = box_column;

alter table laboratory.samples
  drop column cane,
  drop column box,
  drop column box_row,
  drop column box_column
;

alter table laboratory.samples rename column cane2 to cane;
alter table laboratory.samples rename column box2 to box;
alter table laboratory.samples rename column box_row2 to box_row;
alter table laboratory.samples rename column box_column2 to box_column;
