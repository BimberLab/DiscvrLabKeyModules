for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunScGateForModels(seuratObj, modelNames = modelNames, labelRename = list(Tcell = 'T_NK', NK = 'T_NK'))

    saveData(seuratObj, datasetId)
}