for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    dt <- CellMembrane::Find_Markers(seuratObj, identFields = identFields, outFile = paste0(datasetId, '.', fieldName, '.markers.txt'))
    print(dt)
}