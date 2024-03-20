for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  toCalculate <- list()
  for (geneSet in geneSets) {
    vals <- unlist(strsplit(geneSet, split = ':'))
    if (length(vals) != 2) {
      stop(paste0('Improper gene set: ', geneSet))
    }

    geneList <- unlist(strsplit(vals[2], split = ','))
    missingGenes <- dplyr::setdiff(geneList, rownames(seuratObj@assays[[assayName]]))
    if (length(missingGenes) > 0) {
      print(paste0('The following genes were not present in the object and will be skipped: ', paste0(missingGenes, collapse = ',')))
    }

    geneList <- intersect(geneList, rownames(seuratObj@assays[[assayName]]))
    if (length(geneList) == 0) {
      print(paste0('No genes retained, skipping: ', vals[[1]]))
    }

    toCalculate[[vals[1]]] <- geneList
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