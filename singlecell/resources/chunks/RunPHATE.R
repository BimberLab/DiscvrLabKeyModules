for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunPHATE(seuratObj)
    print(DimPlot(seuratObj, reduction = 'phate'))

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
}