for (datasetId in names(seuratObjects)) {
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  seuratObj@commands <- list()
  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}