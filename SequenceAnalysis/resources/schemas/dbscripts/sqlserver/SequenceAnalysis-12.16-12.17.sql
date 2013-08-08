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
--delete any orphan AA records
DELETE FROM sequenceanalysis.ref_aa_sequences WHERE ref_nt_id NOT IN (select rowid from sequenceanalysis.ref_nt_sequences);

ALTER TABLE sequenceanalysis.quality_metrics
  ADD CONSTRAINT fk_quality_metrics_metricname FOREIGN KEY (metricname)
      REFERENCES sequenceanalysis.quality_metrics_types (type)
      ON UPDATE CASCADE;

--NOTE: these deletes were added after initial checkin to hedge against existing installs w/ orphan data
delete from sequenceanalysis.aa_snps where nt_snp_id NOT IN (select rowid from sequenceanalysis.nt_snps);
delete from sequenceanalysis.nt_snps where alignment_id NOT IN (select rowid from sequenceanalysis.sequence_alignments);
delete from sequenceanalysis.ref_aa_features where ref_aa_id not in (select rowid from sequenceanalysis.ref_aa_sequences);
delete from sequenceanalysis.drug_resistance where ref_aa_id not in (select rowid from sequenceanalysis.ref_aa_sequences);
delete from sequenceanalysis.ref_nt_features where ref_nt_id not in (select rowid from sequenceanalysis.ref_nt_sequences);
delete from sequenceanalysis.sequence_alignments where ref_nt_id not in (select rowid from sequenceanalysis.ref_nt_sequences);
delete from sequenceanalysis.sequence_alignments where analysis_id not in (select rowid from sequenceanalysis.sequence_analyses);
delete from sequenceanalysis.sequence_coverage where ref_nt_id not in (select rowid from sequenceanalysis.ref_nt_sequences);

ALTER TABLE sequenceanalysis.aa_snps
  ADD CONSTRAINT fk_aa_snps_nt_snp FOREIGN KEY (nt_snp_id)
      REFERENCES sequenceanalysis.nt_snps (rowid);

ALTER TABLE sequenceanalysis.nt_snps
  ADD CONSTRAINT fk_nt_snps_alignment FOREIGN KEY (alignment_id)
      REFERENCES sequenceanalysis.sequence_alignments (rowid);

ALTER TABLE sequenceanalysis.ref_aa_features
  ADD CONSTRAINT fk_ref_aa_features_ref_aa FOREIGN KEY (ref_aa_id)
      REFERENCES sequenceanalysis.ref_aa_sequences (rowid);

ALTER TABLE sequenceanalysis.drug_resistance
  ADD CONSTRAINT fk_drug_resistance_ref_aa FOREIGN KEY (ref_aa_id)
      REFERENCES sequenceanalysis.ref_aa_sequences (rowid);

ALTER TABLE sequenceanalysis.ref_aa_sequences
  ADD CONSTRAINT fk_ref_aa_sequences_ref_nt FOREIGN KEY (ref_nt_id)
      REFERENCES sequenceanalysis.ref_nt_sequences (rowid);

ALTER TABLE sequenceanalysis.ref_nt_features
  ADD CONSTRAINT fk_ref_nt_features_ref_nt FOREIGN KEY (ref_nt_id)
      REFERENCES sequenceanalysis.ref_nt_sequences (rowid);

ALTER TABLE sequenceanalysis.sequence_alignments
  ADD CONSTRAINT fk_sequence_alignments_ref_nt FOREIGN KEY (ref_nt_id)
      REFERENCES sequenceanalysis.ref_nt_sequences (rowid);

ALTER TABLE sequenceanalysis.sequence_alignments
  ADD CONSTRAINT fk_sequence_alignments_analysis FOREIGN KEY (analysis_id)
      REFERENCES sequenceanalysis.sequence_analyses (rowid);

ALTER TABLE sequenceanalysis.sequence_coverage
  ADD CONSTRAINT fk_sequence_coverage_ref_nt FOREIGN KEY (ref_nt_id)
      REFERENCES sequenceanalysis.ref_nt_sequences (rowid);