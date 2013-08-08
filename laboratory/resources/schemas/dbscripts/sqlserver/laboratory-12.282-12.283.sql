ALTER table laboratory.dna_oligos add cognate_primer_name varchar(100);
ALTER table laboratory.dna_oligos add oligo_id integer;
ALTER table laboratory.dna_oligos drop column cognate_primer;