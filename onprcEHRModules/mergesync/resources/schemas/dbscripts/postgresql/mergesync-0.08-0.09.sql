/*
 * Copyright (c) 2011 LabKey Corporation
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

DELETE FROM mergesync.mergetolkmapping WHERE mergetestname = 'SENSI2';
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('SENSI2', 'Antibiotic Sensitivity');

DELETE FROM mergesync.mergetolkmapping WHERE mergetestname = 'SENSI3';
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('SENSI3', 'Antibiotic Sensitivity');