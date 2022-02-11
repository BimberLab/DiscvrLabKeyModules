for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}