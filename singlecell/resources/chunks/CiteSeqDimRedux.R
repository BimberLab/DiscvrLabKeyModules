for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    #TODO: what if not used?
    seuratObj <- bindArgs(CellMembrane::CiteSeqDimRedux, seuratObj)()

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}