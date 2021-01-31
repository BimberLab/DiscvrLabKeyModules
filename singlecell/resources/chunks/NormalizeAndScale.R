for (datasetId in names(seuratObjects)) {
    # Preemptively cleanup:
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    gc()

    seuratObj <- bindArgs(CellMembrane::NormalizeAndScale, seuratObj)()

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}