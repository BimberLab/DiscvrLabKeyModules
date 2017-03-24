ALTER TABLE sequenceanalysis.ref_nt_features ADD container ENTITYID;
ALTER TABLE sequenceanalysis.ref_aa_features ADD container ENTITYID;

UPDATE sequenceanalysis.ref_nt_features SET container = (select container from sequenceanalysis.ref_nt_sequences WHERE ref_nt_sequences.rowid = ref_nt_features.ref_nt_id);
UPDATE sequenceanalysis.ref_aa_features SET container = (select container from sequenceanalysis.ref_nt_sequences WHERE ref_nt_sequences.rowid = ref_aa_features.ref_nt_id);