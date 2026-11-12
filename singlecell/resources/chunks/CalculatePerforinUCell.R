if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
    nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
    nCores <- 1
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    message(paste0('Loading dataset ', datasetId, ', with total cells: ', ncol(seuratObj)))
    seuratObj <- UCell::AddModuleScore_UCell(seuratObj, features = list(
      PRF1 = c('PRF1'),
      PRF1_GZMB = c('PRF1', 'GZMB')
    ), ncores = nCores, assay = assayName)

    seuratObj <- UCell::SmoothKNN(seuratObj, signature.names = 'PRF1_UCell', 'PRF1_GZMB_UCell')

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}