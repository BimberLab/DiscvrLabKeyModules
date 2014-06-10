/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

-- remove all records from SampleReviewerMap as we need to add a new key field, Status, to the PK
TRUNCATE TABLE biotrust.SampleReviewerMap;
ALTER TABLE biotrust.SampleReviewerMap DROP CONSTRAINT PK_container_tissueid_reviewer;
ALTER TABLE biotrust.SampleReviewerMap ADD Status NVARCHAR(40) NOT NULL;
ALTER TABLE biotrust.SampleReviewerMap ADD CONSTRAINT FK_status_requeststatus FOREIGN KEY (Status) REFERENCES biotrust.RequestStatus (Status);
ALTER TABLE biotrust.SampleReviewerMap ADD CONSTRAINT PK_container_tissueid_reviewer PRIMARY KEY (Container, TissueId, Reviewer, Status);