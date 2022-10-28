for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  toCalculate <- list()
  for (geneSet in geneSets) {
    vals <- unlist(strsplit(geneSet, split = ':'))
    if (length(vals) != 2) {
      stop(paste0('Improper gene set: ', geneSet))
    }

    toCalculate[[vals[1]]] <- unlist(strsplit(vals[2], split = ','))
  }

  seuratObj <- UCell::AddModuleScore_UCell(seuratObj, features = toCalculate)
  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}