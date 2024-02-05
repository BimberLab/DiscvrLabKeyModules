for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (metabolismType in metabolismTypes) {
        seuratObj <- CellMembrane::RunScMetabolism(seuratObj, metabolismType = metabolismType)
    }

    # Cleanup
    rm(seuratObj)
    gc()
}