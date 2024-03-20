for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  toCalculate <- list()
  for (geneSet in geneSets) {
    vals <- unlist(strsplit(geneSet, split = ':'))
    if (length(vals) != 2) {
      stop(paste0('Improper gene set: ', geneSet))
    }

    toCalculate[[vals[1]]] <- unlist(strsplit(vals[2], split = ','))
  }

  seuratObj <- CellMembrane::RunEscape(seuratObj, customGeneSets = toCalculate, outputAssayName = outputAssayName, doPlot = TRUE, msigdbGeneSets = NULL, performDimRedux = performDimRedux)

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}