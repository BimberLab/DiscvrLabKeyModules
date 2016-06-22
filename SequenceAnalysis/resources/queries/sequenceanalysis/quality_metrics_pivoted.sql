select
  dataId,
  max(analysis_id) as analysis_id,
  max(readset) as readset,
  --container,
  group_concat(DISTINCT category) as category,
  count(*) as records,
  metricName,
  avg(metricValue) as metricValue

from sequenceanalysis.quality_metrics q
where (category is null or category not in ('FIRST_OF_PAIR', 'SECOND_OF_PAIR'))
group by dataId, metricName
pivot metricValue by metricName