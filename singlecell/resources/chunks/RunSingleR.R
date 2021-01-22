for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::RunSingleR(seuratObj)

    newSeuratObjects[[datasetId]] <- seuratObj
}