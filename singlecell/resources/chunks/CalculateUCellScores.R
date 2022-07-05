for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    RIRA::CalculateUCellScores(seuratObj)

    # Cleanup
    rm(seuratObj)
    gc()
}