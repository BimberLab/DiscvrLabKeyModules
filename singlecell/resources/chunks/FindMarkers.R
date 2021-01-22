for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    for (fieldName in identFields) {
        dt <- CellMembrane::Find_Markers(seuratObj, fieldName = fieldName, outFile = paste0(datasetId, '.', fieldName, '.markers.txt'))
        print(dt)
    }

    newSeuratObjects[[datasetId]] <- seuratObj
}