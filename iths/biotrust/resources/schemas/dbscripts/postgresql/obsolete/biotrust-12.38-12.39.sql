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

DELETE FROM biotrust.ParticipantEligibilityMap;
DELETE FROM biotrust.SamplePickupMap;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD Container ENTITYID NOT NULL;
ALTER TABLE biotrust.SamplePickupMap ADD Container ENTITYID NOT NULL;

ALTER TABLE biotrust.ParticipantEligibilityMap DROP CONSTRAINT PK_tissueid_eligibilityid;
ALTER TABLE biotrust.SamplePickupMap DROP CONSTRAINT PK_tissueid_pickupid;

ALTER TABLE biotrust.ParticipantEligibilityMap ADD CONSTRAINT PK_tissueid_eligibilityid PRIMARY KEY (TissueId, EligibilityId, Container);
ALTER TABLE biotrust.SamplePickupMap ADD CONSTRAINT PK_tissueid_pickupid PRIMARY KEY (TissueId, PickupId, Container);
