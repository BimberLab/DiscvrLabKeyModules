for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (useRhesusDefaults) {
        seuratObj <- RIRA::RunScGateWithRhesusModels(seuratObj, dropAmbiguousConsensusValues = dropAmbiguousConsensusValues)
    } else {
        seuratObj <- RIRA::RunScGateForModels(seuratObj, modelNames = modelNames, labelRename = list(Tcell = 'T_NK', NK = 'T_NK'), dropAmbiguousConsensusValues = dropAmbiguousConsensusValues, consensusModels = consensusModels)
    }

    saveData(seuratObj, datasetId)
}