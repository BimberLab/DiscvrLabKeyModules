SELECT
  b.tag_name,
  b.sequence,
  b.group_name

FROM sequenceanalysis.barcodes b
WHERE group_name IN ('5p-HTOs', 'MultiSeq Barcodes')