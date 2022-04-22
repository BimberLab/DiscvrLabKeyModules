for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- bindArgs(CellMembrane::FilterRawCounts, seuratObj)()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}