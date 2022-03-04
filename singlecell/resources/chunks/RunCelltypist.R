for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::RunCellTypist(seuratObj)

    saveData(seuratObj, datasetId)
}