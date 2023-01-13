for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    for (mn in modelNames) {
        seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = paste0(mn, '.pkl'), columnPrefix = paste0('celltypist.', mn, '.'), pThreshold = pThreshold, minProp = minProp, maxAllowableClasses = maxAllowableClasses, minFractionToInclude = minFractionToInclude, useMajorityVoting = useMajorityVoting, mode = mode, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    }

    saveData(seuratObj, datasetId)
}