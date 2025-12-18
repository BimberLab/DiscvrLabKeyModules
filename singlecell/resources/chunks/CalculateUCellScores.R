if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
    nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
    nCores <- 1
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    message(paste0('Loading dataset ', datasetId, ', with total cells: ', ncol(seuratObj)))
    seuratObj <- RIRA::CalculateUCellScores(seuratObj, storeRanks = storeRanks, assayName = assayName, forceRecalculate = forceRecalculate, ncores = nCores, dropAllExistingUcells = dropAllExistingUcells)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}