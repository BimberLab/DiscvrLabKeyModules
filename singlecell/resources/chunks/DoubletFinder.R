for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::FindDoublets(seuratObj, dropDoublets = dropDoublets)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}