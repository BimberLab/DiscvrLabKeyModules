for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = 'RIRA_Immune_v1', maxBatchSize = maxBatchSize, columnPrefix = 'RIRA_Immune_v1.', retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix, columnPrefix = 'RIRA_TNK_v2.')

    seuratObj$RIRA_TNK_v2.predicted_labels[seuratObj$RIRA_Immune_v1.majority_voting != 'T_NK'] <- 'Other'

    saveData(seuratObj, datasetId)
}