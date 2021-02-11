for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    outFile = paste0(datasetId, '.markers.txt')
    dt <- bindArgs(CellMembrane::Find_Markers, seuratObj)()
    print(dt)

    # Cleanup
    rm(seuratObj)
    gc()
}