for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    RIRA::TrainCelltypist(seuratObj, labelField = labelField, minCellsPerClass = minCellsPerClass, modelFile = modelFile)
}