for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (!(assayName %in% names(seuratObj@assays))) {
        print(paste0(assayName, ' assay not present, skipping'))
    } else {
        seuratObj <- bindArgs(CellMembrane::CiteSeqDimRedux.PCA, seuratObj)()
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}