for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    newList <- CellMembrane::SplitSeurat(seuratObj, splitField = splitField, minCellsToKeep = minCellsToKeep)
    for (name in names(newList)) {
        newSeuratObjects[[paste0(datasetId, '-', name)]] <- newList[[name]]
    }

    # Cleanup
    rm(seuratObj)
    gc()
}