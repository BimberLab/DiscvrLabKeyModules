for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    newList <- CellMembrane::SplitSeurat(seuratObj, splitField = splitField, minCellsToKeep = minCellsToKeep)
    for (name in names(newList)) {
        newSeuratObjects[[paste0(datasetId, '-', name)]] <- newList[[name]]
    }

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}