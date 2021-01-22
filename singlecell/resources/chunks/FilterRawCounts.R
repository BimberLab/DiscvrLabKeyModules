for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObj <- CellMembrane::FilterRawCounts(seuratObj, targetCells = targetCells, subsetFields = subsetFields, seed = seed)

    newSeuratObjects[[datasetId]] <- seuratObj
}