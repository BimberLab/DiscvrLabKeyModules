for (datasetId in names(seuratObjects)) {
    rawCountDir <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = rawCountDir, datasetId = datasetId, datasetName = datasetName, emptyDropsLower = emptyDropsLower, emptyDropsFdrThreshold = emptyDropsFdrThreshold)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}