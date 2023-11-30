for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    RIRA::PlotImmuneMarkers(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}