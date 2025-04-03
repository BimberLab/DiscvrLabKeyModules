for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunEscape(seuratObj, outputAssayBaseName = outputAssayBaseName, doPlot = TRUE, performDimRedux = performDimRedux)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}