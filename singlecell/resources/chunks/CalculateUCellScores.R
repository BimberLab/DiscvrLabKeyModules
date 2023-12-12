for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    message(paste0('Loading dataset ', datasetId, ', with total cells: ', ncol(seuratObj)))
    seuratObj <- RIRA::CalculateUCellScores(seuratObj, storeRanks = storeRanks, assayName = assayName, forceRecalculate = forceRecalculate)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}