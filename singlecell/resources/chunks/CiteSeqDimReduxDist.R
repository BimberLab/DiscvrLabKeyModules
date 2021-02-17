for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        tryCatch({
            seuratObj <- bindArgs(CellMembrane::CiteSeqDimRedux.Dist, seuratObj)()
        }, error = function(e){
            conditionMessage(e)
            print(paste0('Error running CiteSeqDimRedux.Dist'))
            print(conditionMessage(e))
            traceback()
        })
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}