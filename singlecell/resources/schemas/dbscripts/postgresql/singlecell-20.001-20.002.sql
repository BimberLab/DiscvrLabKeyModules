ALTER TABLE singlecell.citeseq_antibodies ADD barcodepattern VARCHAR(100);

UPDATE singlecell.citeseq_antibodies SET barcodePattern = '5PNNNNNNNNNN(BC)' WHERE barcodeName LIKE '%TotalSeq-C%';