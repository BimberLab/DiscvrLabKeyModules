if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
    nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
    nCores <- 1
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunEscape(seuratObj, outputAssayBaseName = outputAssayBaseName, doPlot = TRUE, heatmapGroupingVars = heatmapGroupingVars, performDimRedux = performDimRedux, escapeMethod = escapeMethod, nCores = nCores)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}