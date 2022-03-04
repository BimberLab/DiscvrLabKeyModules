for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        for (adt in rownames(seuratObj[['ADT']])) {
            CellMembrane::FeaturePlotAcrossReductions(seuratObj, features = paste0('adt_', adt))
        }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}