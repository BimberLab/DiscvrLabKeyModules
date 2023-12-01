for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::Classify_ImmuneCells(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)

    seuratObj$RIRA_TNK_v2.predicted_labels[seuratObj$RIRA_Immune_v2.majority_voting != 'T_NK'] <- 'Other'

    saveData(seuratObj, datasetId)
}