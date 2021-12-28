for (datasetId in names(seuratObjects)) {
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  if (!('ADT' %in% names(seuratObj@assays))) {
    print('ADT assay not present, skipping')
  } else {
    CellMembrane::PlotAverageAdtCounts(seuratObj, groupFields = fieldNames)
  }

  # Cleanup
  rm(seuratObj)
}