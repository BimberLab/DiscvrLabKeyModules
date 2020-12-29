SELECT
  t3.sortPlateId,
  t3.container,
  t3.workbook,
  t3.sortType,
  t3.processingRequested,
  t3.subjects,
  t3.stims,
  t3.sampleDates,
  t3.totalSorts,
  t3.totalBulkSorts,
  t3.totalSingleCells,
  t3.totalLibraries,
  t3.totalLibrariesWithData,
  t3.totalLibrariesWithBulkData,
  t3.totalLibrariesWithEnrichedData,
  t3.librariesComplete,
  t3.sequencingComplete,
  CASE
    WHEN (t3.librariesComplete = TRUE AND t3.sequencingComplete = TRUE) THEN TRUE
    ELSE FALSE
  END as isComplete
  
FROM (
SELECT
  t2.sortPlateId,
  t2.container,
  t2.workbook,
  t2.subjects,
  t2.stims,
  t2.sampleDates,
  t2.totalSorts,
  t2.totalBulkSorts,
  t2.totalSingleCells,
  t2.totalLibraries,
  t2.totalLibrariesWithData,
  t2.totalLibrariesWithBulkData,
  t2.totalLibrariesWithEnrichedData,
  CASE WHEN t2.totalSorts - t2.totalLibraries <= 0 THEN true ELSE false END as librariesComplete,
  CASE
    WHEN (t2.processingRequested LIKE '%Whole Transcriptome%' AND t2.totalSorts - t2.totalLibrariesWithBulkData > 0) THEN FALSE
    WHEN (t2.processingRequested LIKE '%Enriched%' AND t2.totalSorts - t2.totalLibrariesWithEnrichedData > 0) THEN FALSE
    WHEN (t2.totalSorts - t2.totalLibrariesWithData > 0) THEN FALSE
    ELSE TRUE
  END as sequencingComplete,
  CASE
    WHEN (t2.totalBulkSorts > 0 AND t2.totalSingleCells > 0) THEN 'MIXED'
    WHEN (t2.totalBulkSorts > 0) THEN 'BULK'
    WHEN (t2.totalSingleCells > 0) THEN 'SINGLE'
  END as sortType,
  t2.processingRequested
FROM (
SELECT
  t.plateId as sortPlateId,
  t.totalSorts,
  t.totalBulkSorts,
  t.totalSingleCells,
  t.subjects,
  t.stims,
  t.sampleDates,
  t.container,
  t.workbook,
  t.processingRequested,
  (SELECT count(*) as expr  from singlecell.cdna_libraries c1 WHERE c1.sortId.plateId = t.plateId) as totalLibraries,
  (SELECT count(*) as expr  from singlecell.cdna_libraries c2 WHERE c2.sortId.plateId = t.plateId AND c2.hasReadsetWithData = true) as totalLibrariesWithData,
  (SELECT count(*) as expr  from singlecell.cdna_libraries c3 WHERE c3.sortId.plateId = t.plateId AND c3.readsetId.totalFiles > 0) as totalLibrariesWithBulkData,
  (SELECT count(*) as expr  from singlecell.cdna_libraries c4 WHERE c4.sortId.plateId = t.plateId AND c4.tcrReadsetId.totalFiles > 0) as totalLibrariesWithEnrichedData,
  (SELECT group_concat(distinct c3.plateId, chr(10)) as expr from singlecell.cdna_libraries c3 WHERE c3.sortId.plateId = t.plateId) as libraryPlates

FROM (
  SELECT
    s.plateId,
    s.container,
    s.workbook,
    count(*) AS totalSorts,
    SUM(CASE WHEN s.cells > 1 THEN 1 ELSE 0 END) AS totalBulkSorts,
    SUM(CASE WHEN s.cells = 1 THEN 1 ELSE 0 END) AS totalSingleCells,
    group_concat(distinct s.sampleId.subjectId) as subjects,
    group_concat(distinct s.sampleId.stim) as stims,
    group_concat(distinct ((year(s.sampleId.sampledate) || '-' || month(s.sampleId.sampledate) || '-' || dayofmonth(s.sampleId.sampledate)))) as sampleDates,
    group_concat(distinct s.processingRequested) as processingRequested

  FROM singlecell.sorts s
  GROUP BY s.plateId, s.container, s.workbook

) t
) t2
) t3