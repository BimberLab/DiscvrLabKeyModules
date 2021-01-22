for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::NormalizeAndScale(seuratObj, variableFeatureSelectionMethod = variableFeatureSelectionMethod)

    newSeuratObjects[[datasetId]] <- seuratObj
}