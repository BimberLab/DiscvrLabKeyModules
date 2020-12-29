SELECT
  distinct name,
  count(*) as totalMarkers

FROM singlecell.citeseq_panels
GROUP BY name