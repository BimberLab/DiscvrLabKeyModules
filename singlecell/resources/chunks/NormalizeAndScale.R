for (datasetId in names(seuratObjects)) {
    # Preemptively cleanup:
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    gc()

    seuratObj <- CellMembrane::NormalizeAndScale(seuratObj, variableFeatureSelectionMethod = variableFeatureSelectionMethod, block.size = block.size)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}