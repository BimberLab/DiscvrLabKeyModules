for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::DownsampleSeurat(seuratObj, targetCells = targetCells, subsetFields = subsetFields, seed = seed)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}