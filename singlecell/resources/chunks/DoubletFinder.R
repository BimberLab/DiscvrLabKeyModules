for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindDoublets(seuratObj, dropDoublets = dropDoublets)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}