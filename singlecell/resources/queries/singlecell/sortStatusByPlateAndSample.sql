SELECT
  t3.sortPlateId,
  t3.container,
  t3.workbook,
  t3.sortType,
  t3.subjectId,
  t3.stim,
  t3.sampledate,
  t3.totalSorts,
  t3.totalBulkSorts,
  t3.totalSingleCells,
  t3.totalLibraries,
  t3.totalLibrariesWithData,
  t3.totalLibrariesWithBulkData,
  t3.totalLibrariesWithEnrichedData,
  t3.librariesComplete

FROM (
SELECT
  t2.sortPlateId,
  t2.container,
  t2.workbook,
  t2.subjectId,
  t2.stim,
  t2.sampledate,
  t2.totalSorts,
  t2.totalBulkSorts,
  t2.totalSingleCells,
  t2.totalLibraries,
  t2.totalLibrariesWithData,
  t2.totalLibrariesWithBulkData,
  t2.totalLibrariesWithEnrichedData,
  CASE WHEN t2.totalSorts - t2.totalLibraries <= 0 THEN true ELSE false END as librariesComplete,
  CASE
    WHEN (t2.totalBulkSorts > 0 AND t2.totalSingleCells > 0) THEN 'MIXED'
    WHEN (t2.totalBulkSorts > 0) THEN 'BULK'
    WHEN (t2.totalSingleCells > 0) THEN 'SINGLE'
  END as sortType
FROM (
SELECT
  t.plateId as sortPlateId,
  t.totalSorts,
  t.totalBulkSorts,
  t.totalSingleCells,
  t.subjectId,
  t.stim,
  t.sampledate,
  t.container,
  t.workbook,
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
    s.sampleId.subjectId,
    s.sampleId.stim,
    s.sampleId.sampledate,
    count(*) AS totalSorts,
    SUM(CASE WHEN s.cells > 1 THEN 1 ELSE 0 END) AS totalBulkSorts,
    SUM(CASE WHEN s.cells = 1 THEN 1 ELSE 0 END) AS totalSingleCells
  FROM singlecell.sorts s
  GROUP BY s.plateId, s.container, s.workbook, s.sampleId.subjectId, s.sampleId.stim, s.sampleId.sampledate

) t
) t2
) t3