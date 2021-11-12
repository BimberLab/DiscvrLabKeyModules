for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    CellMembrane::PlotImmuneMarkers(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}