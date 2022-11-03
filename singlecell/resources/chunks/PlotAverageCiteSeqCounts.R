for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  if (!('ADT' %in% names(seuratObj@assays))) {
    print('ADT assay not present, skipping')
  } else {
    tryCatch({
      CellMembrane::PlotAverageAdtCounts(seuratObj, groupFields = fieldNames, assayName = assayName)
    }, error = function(e){
      print(paste0('Error running PlotAverageCiteSeqCounts for: ', datasetId))
      print(conditionMessage(e))
      traceback()
    })
  }

  # Cleanup
  rm(seuratObj)
}