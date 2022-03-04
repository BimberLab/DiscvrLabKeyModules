for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    CellMembrane::PlotImmuneMarkers(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}