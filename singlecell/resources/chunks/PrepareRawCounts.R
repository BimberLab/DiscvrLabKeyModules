for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObjs[[datasetId]] <- CellMembrane::ReadAndFilter10xData(dataDir = data[[datasetId]], datasetName = datasetName)

    newSeuratObjects[[datasetId]] <- seuratObj
}