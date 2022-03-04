for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}