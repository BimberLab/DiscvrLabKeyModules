for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    CellMembrane::PlotImmuneMarkers(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}