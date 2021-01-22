for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::SubsetSeurat(seuratObj, expressions = expressions)

    newSeuratObjects[[datasetId]] <- seuratObj
}