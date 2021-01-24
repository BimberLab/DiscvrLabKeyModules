for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObjs[[datasetId]] <- CellMembrane::ReadAndFilter10xData(dataDir = seuratObjects[[datasetId]], datasetName = datasetName)

    newSeuratObjects[[datasetId]] <- seuratObj
}