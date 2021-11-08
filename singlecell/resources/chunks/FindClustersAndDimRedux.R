for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindClustersAndDimRedux(seuratObj, minDimsToUse = minDimsToUse)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}