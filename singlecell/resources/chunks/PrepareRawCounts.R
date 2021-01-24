for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = seuratObjects[[datasetId]], datasetId = datasetId, datasetName = datasetName)

    newSeuratObjects[[datasetId]] <- seuratObj
}