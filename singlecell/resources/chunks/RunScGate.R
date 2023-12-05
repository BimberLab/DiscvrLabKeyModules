for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunScGateWithRhesusModels(seuratObj, dropAmbiguousConsensusValues = dropAmbiguousConsensusValues, assay = assayName)

    saveData(seuratObj, datasetId)
}