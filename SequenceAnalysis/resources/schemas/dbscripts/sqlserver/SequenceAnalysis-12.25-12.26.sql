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
ALTER TABLE sequenceanalysis.quality_metrics add analysis_id integer;

ALTER TABLE sequenceanalysis.aa_snps_by_codon add ref_nt_positions varchar(200);

INSERT into sequenceanalysis.quality_metrics_types (type) VALUES ('%Reads Aligned In Pairs');
INSERT into sequenceanalysis.quality_metrics_types (type) VALUES ('Total Sequences Passed Filter');
INSERT into sequenceanalysis.quality_metrics_types (type) VALUES ('Reads Aligned');
INSERT into sequenceanalysis.quality_metrics_types (type) VALUES ('%Reads Aligned');
