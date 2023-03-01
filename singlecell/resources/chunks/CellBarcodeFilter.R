totalPassed <- 0
for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  cellsToKeep <- colnames(seuratObj)
  if (!all(is.null(cellbarcodesToDrop))) {
    cellsToKeep <- cellsToKeep[!cellsToKeep %in% cellbarcodesToDrop]
  }

  if (length(cellsToKeep) == 0) {
    print('There were no matching cells')
  } else {
    print(paste0('Total passing cells: ', length(cellsToKeep)))
    seuratObj <- subset(seuratObj, cells = cellsToKeep)
    saveData(seuratObj, datasetId)
    totalPassed <- totalPassed + 1
  }

  # Cleanup
  rm(seuratObj)
  gc()
}

if (totalPassed == 0) {
  addErrorMessage('No cells remained in any seurat objects after subsetting')
}