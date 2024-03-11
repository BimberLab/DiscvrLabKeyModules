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

  seuratObj <- UCell::AddModuleScore_UCell(seuratObj, features = toCalculate, storeRanks = storeRanks, assay = assayName)
  for (n in names(toCalculate)) {
    print(Seurat::FeaturePlot(seuratObj, features = paste0(n, '_UCell'), min.cutoff = 'q02', max.cutoff = 'q98'))
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}