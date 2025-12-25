if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
    nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
    nCores <- 1
}

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  toDelete <- c()

  vals <- eval(formals(CellMembrane::RunEscape)$msigdbGeneSets)
  for (idx in seq_along(vals)) {
    geneSetName <- names(vals)[idx]
    geneSet <- vals[[idx]]
    logger::log_info(paste0('Processing: ', geneSetName, ' / ', geneSet))

    fn <- paste0('escape.', datasetId, '.', ifelse(geneSetName == '', yes = geneSet, no = geneSetName), '.rds')
    if (file.exists(fn)) {
      logger::log_info(paste0('resuming: ', fn))
      seuratObj <- readRDS(fn)
      toDelete <- c(toDelete, fn)
    } else {
      msigdbGeneSets <- geneSet
      if (geneSetName != '') {
        names(msigdbGeneSets) <- geneSetName
      }

      seuratObj <- CellMembrane::RunEscape(seuratObj, msigdbGeneSets = msigdbGeneSets, outputAssayBaseName = outputAssayBaseName, doPlot = TRUE, heatmapGroupingVars = heatmapGroupingVars, performDimRedux = performDimRedux, escapeMethod = escapeMethod, nCores = nCores)
      saveRDS(seuratObj, file = fn)
      toDelete <- c(toDelete, fn)
    }
  }

  for(fn in toDelete) {
    unlink(fn)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}