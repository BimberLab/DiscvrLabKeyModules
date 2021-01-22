for (datasetName in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObjs[[datasetName]] <- ReadAndFilter10xData(dataDir = data[[datasetName]], datasetName = datasetName, gtfFile = NULL)

    newSeuratObjects[[datasetId]] <- seuratObj
}