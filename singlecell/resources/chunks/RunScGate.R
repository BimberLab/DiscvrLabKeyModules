for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunScGateWithRhesusModels(seuratObj, dropAmbiguousConsensusValues = dropAmbiguousConsensusValues, assay = assayName)

    saveData(seuratObj, datasetId)
}