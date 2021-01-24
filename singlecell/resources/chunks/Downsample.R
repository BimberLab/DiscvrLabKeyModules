for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    CellMembrane::DownsampleSeurat(seuratObj, targetCells = targetCells, subsetFields = subsetFields, seed = seed)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}