for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindClustersAndDimRedux(seuratObj, minDimsToUse = minDimsToUse, useLeiden = useLeiden)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}