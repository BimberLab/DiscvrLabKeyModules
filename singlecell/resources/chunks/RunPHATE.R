for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunPHATE(seuratObj, t = ifelse(is.null(phateT), yes = 'auto', no = phateT))
    print(DimPlot(seuratObj, reduction = 'phate'))

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
}