/*
 * Copyright (c) 2013 LabKey Corporation
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

-- remove Container column from RequestCategory and RequestStatus tables
ALTER TABLE biotrust.RequestCategory DROP CONSTRAINT UQ_ContainerCategory;
ALTER TABLE biotrust.RequestCategory DROP CONSTRAINT UQ_ContainerSortOrder;
ALTER TABLE biotrust.RequestCategory DROP COLUMN Container;
ALTER TABLE biotrust.RequestStatus DROP COLUMN Container;

-- add SortOrder column to RequestStatus
ALTER TABLE biotrust.RequestStatus ADD COLUMN SortOrder REAL;
