for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::RemoveCellCycle(seuratObj)

    newSeuratObjects[[datasetId]] <- seuratObj
}