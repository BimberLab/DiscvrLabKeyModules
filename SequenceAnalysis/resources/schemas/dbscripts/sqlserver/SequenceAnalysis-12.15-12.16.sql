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
DROP TABLE sequenceanalysis.module_properties;

ALTER TABLE sequenceanalysis.aa_snps
  ADD CONSTRAINT fk_aa_snps_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.quality_metrics
  ADD CONSTRAINT fk_quality_metrics_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.nt_snps
  ADD CONSTRAINT fk_nt_snps_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.sequence_alignments
  ADD CONSTRAINT fk_sequence_alignments_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.sequence_analyses
  ADD CONSTRAINT fk_sequence_analyses_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.sequence_coverage
  ADD CONSTRAINT fk_sequence_coverage_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.sequence_reads
  ADD CONSTRAINT fk_sequence_reads_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);

ALTER TABLE sequenceanalysis.sequence_readsets
  ADD CONSTRAINT fk_sequence_readsets_container FOREIGN KEY (container)
      REFERENCES core.containers (entityid);
