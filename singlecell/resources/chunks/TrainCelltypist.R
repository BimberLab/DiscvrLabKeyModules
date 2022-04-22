if (length(names(seuratObjects)) > 1) {
    stop('Training expects a single input. Either include a merge step upstream or run jobs on individual input files')
}

datasetId <- names(seuratObjects)[[1]]
printName(datasetId)
seuratObj <- readRDS(seuratObjects[[datasetId]])

RIRA::TrainCellTypist(seuratObj, labelField = labelField, minCellsPerClass = minCellsPerClass, excludedClasses = excludedClasses, modelFile = modelFile, tempFileLocation = '/work')