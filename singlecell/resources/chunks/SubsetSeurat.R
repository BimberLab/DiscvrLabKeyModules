for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::SubsetSeurat(seuratObj, expressionStrings = expressionStrings)

    newSeuratObjects[[datasetId]] <- seuratObj
}