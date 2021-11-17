for (datasetId in names(seuratObjects)) {
    rawCountDir <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = rawCountDir, datasetId = datasetId, datasetName = datasetName)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}