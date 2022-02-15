for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = modelFile)

    saveData(seuratObj, datasetId)
}