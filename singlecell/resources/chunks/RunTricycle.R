 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunTricycle(seuratObj)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}