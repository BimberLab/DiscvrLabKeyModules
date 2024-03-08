for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::PredictScTourPseudotime(seuratObj, modelFile = modelFile)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}