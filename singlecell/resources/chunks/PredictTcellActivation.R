for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  seuratObj <- RIRA::PredictTcellActivation(seuratObj)

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}