for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        seuratObj <- bindArgs(CellMembrane::CiteSeqDimRedux.PCA, seuratObj)()
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}