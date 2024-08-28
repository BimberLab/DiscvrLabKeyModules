for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::Classify_ImmuneCells(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)

    seuratObj <- RIRA::Classify_Myeloid(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)

    saveData(seuratObj, datasetId)
}