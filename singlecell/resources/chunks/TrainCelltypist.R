for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    RIRA::TrainCellTypist(seuratObj, labelField = labelField, minCellsPerClass = minCellsPerClass, modelFile = modelFile, tempFileLocation = '/work')
}