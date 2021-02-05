for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    dt <- CellMembrane::Find_Markers(seuratObj, identFields = identFields, outFile = paste0(datasetId, '.markers.txt'))
    print(dt)

    # Cleanup
    rm(seuratObj)
    gc()
}