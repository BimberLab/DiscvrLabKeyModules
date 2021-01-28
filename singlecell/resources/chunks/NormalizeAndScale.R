for (datasetId in names(seuratObjects)) {
    options('Seurat.memsafe' = TRUE)

    # Preemptively cleanup:
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    gc()

    seuratObj <- CellMembrane::NormalizeAndScale(seuratObj, variableFeatureSelectionMethod = variableFeatureSelectionMethod)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}