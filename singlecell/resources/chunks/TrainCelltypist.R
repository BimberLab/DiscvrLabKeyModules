for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    RIRA::TrainCellTypist(seuratObj, labelField = labelField, minCellsPerClass = minCellsPerClass, modelFile = modelFile, tempFileLocation = '/work')
}