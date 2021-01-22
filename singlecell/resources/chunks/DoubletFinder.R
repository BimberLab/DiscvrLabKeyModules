for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::FindDoublets(seuratObj)

    newSeuratObjects[[datasetId]] <- seuratObj
}