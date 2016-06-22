update sequenceanalysis.quality_metrics
set metricName = 'Avg Read Length' where metricName = 'Avg Sequence Length';

update sequenceanalysis.quality_metrics
set metricName = 'Min Read Length' where metricName = 'Min Sequence Length';

update sequenceanalysis.quality_metrics
set metricName = 'Max Read Length' where metricName = 'Max Sequence Length';

update sequenceanalysis.quality_metrics
set metricName = 'Total Reads' where metricName = 'Total Sequences';