for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunScGateForModels(seuratObj, modelNames = modelNames, dropAmbiguousConsensusValues = dropAmbiguousConsensusValues, consensusModels = consensusModels, assay = assayName)

    saveData(seuratObj, datasetId)
}