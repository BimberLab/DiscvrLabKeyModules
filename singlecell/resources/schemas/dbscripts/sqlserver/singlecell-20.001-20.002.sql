ALTER TABLE singlecell.citeseq_antibodies ADD barcodePattern VARCHAR(100);
GO
UPDATE singlecell.citeseq_antibodies SET barcodePattern = '5PNNNNNNNNNN(BC)' WHERE barcodeName LIKE '%TotalSeq-C%';