for (datasetId in names(seuratObjects)) {
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  if (!('ADT' %in% names(seuratObj@assays))) {
    print('ADT assay not present, skipping')
  } else {
    tryCatch({
      CellMembrane::PlotAverageAdtCounts(seuratObj, groupFields = fieldNames)
    }, error = function(e){
      print(paste0('Error running PlotAverageCiteSeqCounts for: ', datasetId))
      print(conditionMessage(e))
      traceback()
    })
  }

  # Cleanup
  rm(seuratObj)
}