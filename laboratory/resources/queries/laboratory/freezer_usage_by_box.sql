/*
 * Copyright (c) 2012 LabKey Corporation
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
SELECT
t1.freezer,
t1.cane,
t1.box,

t1.total as total_used,

(t2.rows * t2.columns) as spaces_available,
(t2.rows * t2.columns) - t1.total as empty,
t1.container

FROM (
  SELECT
  s.freezer,
  s.cane,
  s.box,

  count(s.rowid) as total,
  s.workbook.parentContainer as container

  FROM laboratory.samples s
  WHERE s.dateremoved IS NULL

  GROUP BY s.workbook.parentContainer, s.freezer, s.cane, s.box
) t1

LEFT JOIN laboratory.freezers t2 ON (t1.freezer = t2.name AND t1.container = t2.container)