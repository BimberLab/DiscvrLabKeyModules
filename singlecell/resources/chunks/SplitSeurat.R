for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    newList <- CellMembrane::SplitSeurat(seuratObj, splitField = splitField, minCellsToKeep = minCellsToKeep)
    for (name in names(newList)) {
        saveData(newList[[name]], paste0(datasetId, '-', name))
    }

    # Cleanup
    rm(seuratObj)
    gc()
}