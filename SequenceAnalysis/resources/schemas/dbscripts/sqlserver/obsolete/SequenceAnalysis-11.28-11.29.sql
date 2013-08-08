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

ALTER TABLE sequenceanalysis.sequence_alignments
   ALTER COLUMN read_id integer NULL;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_percent double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_percent double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_depth double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_depth double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN adj_reads double precision;

ALTER TABLE sequenceanalysis.aa_snps
   ALTER COLUMN raw_reads double precision;
