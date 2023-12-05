for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindClustersAndDimRedux(seuratObj, minDimsToUse = minDimsToUse)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}