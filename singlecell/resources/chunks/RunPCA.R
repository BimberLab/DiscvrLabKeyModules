for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunPcaSteps(seuratObj, npcs = npcs)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}