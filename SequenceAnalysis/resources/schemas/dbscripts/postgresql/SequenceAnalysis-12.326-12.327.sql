ALTER TABLE sequenceanalysis.aa_snps_by_codon ALTER column ref_aa type VARCHAR(4000);
ALTER TABLE sequenceanalysis.aa_snps_by_codon ALTER column q_aa type VARCHAR(4000);

ALTER TABLE sequenceanalysis.nt_snps_by_pos ALTER column ref_nt type VARCHAR(4000);
ALTER TABLE sequenceanalysis.nt_snps_by_pos ALTER column q_nt type VARCHAR(4000);