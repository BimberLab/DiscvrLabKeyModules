for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunCsCore(seuratObj, saveFile = 'cscoreResults.rds')

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}