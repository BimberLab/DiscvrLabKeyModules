UPDATE sequenceanalysis.quality_metrics SET metricname = 'Mean Read Length' WHERE metricname = 'Avg Read Length';

UPDATE sequenceanalysis.quality_metrics SET category = 'Readset' WHERE category IS NULL and metricname IN (
  'Total Reads',
  'Min Read Length',
  'Max Read Length',
  'Mean Read Length',
  'Total Bases',
  'Total MBases',
  'Total GBases',
  'Total Q10 Bases',
  'Total Q20 Bases',
  'Total Q30 Bases',
  'Total Q40 Bases',
  'Pct Q10',
  'Pct Q20',
  'Pct Q30',
  'Pct Q40'
);

