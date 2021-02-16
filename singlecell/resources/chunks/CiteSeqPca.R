for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!(assayName %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        seuratObj <- bindArgs(CellMembrane::RunAdtPca, seuratObj)()
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}