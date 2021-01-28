for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    dt <- CellMembrane::Find_Markers(seuratObj, identFields = identFields, outFile = paste0(datasetId, '.markers.txt'))
    print(dt)

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}