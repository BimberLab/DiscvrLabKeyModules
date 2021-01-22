for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    for (fieldName in identFields) {
        OOSAP::Find_Markers(seuratObj, fieldName = fieldName, outFile = paste0(datasetId, '.', fieldName, '.markers.txt'))
    }

    newSeuratObjects[[datasetId]] <- seuratObj
}