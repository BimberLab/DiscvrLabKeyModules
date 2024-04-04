for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunEscape(seuratObj, outputAssayName = outputAssayName, doPlot = TRUE, performDimRedux = performDimRedux)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}