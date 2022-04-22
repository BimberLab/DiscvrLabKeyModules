for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!'DatasetId' %in% names(seuratObj@meta.data)) {
        stop('Seurat object lacks a DatasetId field!')
    }

    seuratObj <- CellMembrane::AppendPerCellSaturationInBulk(seuratObj, molInfoList = molInfoFiles)
    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}