for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = seuratObjects[[datasetId]], datasetId = datasetId, datasetName = datasetName)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}