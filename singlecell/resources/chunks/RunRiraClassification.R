for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = 'RIRA_Immune_v1', maxBatchSize = maxBatchSize, columnPrefix = 'RIRA_Immune_v1.', retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)

    saveData(seuratObj, datasetId)
}