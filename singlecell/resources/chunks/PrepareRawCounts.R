for (datasetId in names(seuratObjects)) {
    rawCountDir <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = rawCountDir, datasetId = datasetId, datasetName = datasetName)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}