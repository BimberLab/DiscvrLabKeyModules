for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  seuratObj <- clearSeuratCommands(seuratObj)
  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}