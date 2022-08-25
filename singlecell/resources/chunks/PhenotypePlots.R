for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    RIRA::PlotImmuneMarkers(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}