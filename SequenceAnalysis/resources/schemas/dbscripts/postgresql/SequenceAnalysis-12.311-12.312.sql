ALTER TABLE sequenceanalysis.ref_aa_sequences ADD container ENTITYID;

UPDATE sequenceanalysis.ref_aa_sequences SET container = (select container from sequenceanalysis.ref_nt_sequences WHERE ref_nt_sequences.rowid = ref_aa_sequences.ref_nt_id);
