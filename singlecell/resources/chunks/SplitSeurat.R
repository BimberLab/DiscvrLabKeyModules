for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    newList <- CellMembrane::SplitSeurat(seuratObj, splitField = splitField)
    for (name in names(newList)) {
        newSeuratObjects[[name]] <- newList[[name]]
    }
}