for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = modelFile, columnPrefix = columnPrefix, convertAmbiguousToNA = convertAmbiguousToNA, pThreshold = pThreshold, minProp = minProp, maxAllowableClasses = maxAllowableClasses, minFractionToInclude = minFractionToInclude, useMajorityVoting = useMajorityVoting, mode = mode, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)

    saveData(seuratObj, datasetId)
}