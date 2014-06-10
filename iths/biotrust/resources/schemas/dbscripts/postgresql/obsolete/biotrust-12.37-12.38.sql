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

-- add 3 new text fields for the sample pickup lab information
ALTER TABLE biotrust.SamplePickup ADD COLUMN SampleTestLocation VARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD COLUMN LabBuilding VARCHAR(4000);
ALTER TABLE biotrust.SamplePickup ADD COLUMN LabRoom VARCHAR(4000);

-- drop not null contraint on ArrangeForPickup and HoldOvernight columns
ALTER TABLE biotrust.SamplePickup ALTER COLUMN ArrangeForPickup DROP NOT NULL;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN ArrangeForPickup DROP DEFAULT;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN HoldOvernight DROP NOT NULL;
ALTER TABLE biotrust.SamplePickup ALTER COLUMN HoldOvernight DROP DEFAULT;
