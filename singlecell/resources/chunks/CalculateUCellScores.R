for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    message(paste0('Loading dataset ', datasetId, ', with total cells: ', ncol(seuratObj)))
    seuratObj <- RIRA::CalculateUCellScores(seuratObj)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}