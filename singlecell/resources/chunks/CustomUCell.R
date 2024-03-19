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
  corData <- RIRA::PlotUcellCorrelation(seuratObj, toCalculate)
  saveRDS(corData, file = paste0(datasetId, '.ucellcorr.rds'))

  for (n in names(toCalculate)) {
    print(Seurat::FeaturePlot(seuratObj, features = paste0(n, '_UCell'), min.cutoff = 'q02', max.cutoff = 'q98'))
  }

  if (!is.null(outputAssayName)) {
    dat <- as.matrix(seuratObj@meta.data[paste0(names(toCalculate), '_UCell')])
    rownames(dat) <- rownames(seuratObj@meta.data)
    colnames(dat) <- gsub(colnames(dat), pattern = '_', replacement = '-')
    seuratObj[[outputAssayName]] <- Seurat::CreateAssayObject(data = Matrix::t(dat))
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}