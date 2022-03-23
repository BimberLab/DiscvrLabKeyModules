for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj, convertAmbiguousToNA = convertAmbiguousToNA, columnPrefix = columnPrefix, pThreshold = pThreshold, minProp = minProp)

    saveData(seuratObj, datasetId)
}