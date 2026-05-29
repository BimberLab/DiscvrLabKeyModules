for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunStarCAT(seuratObj, reference = reference, assayName = assayName, outputDirectory = tempfile(pattern = 'starcat_'))

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}
