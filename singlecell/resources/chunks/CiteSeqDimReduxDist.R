for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        tryCatch({
            seuratObj <- bindArgs(CellMembrane::CiteSeqDimRedux.Dist, seuratObj)()
        }, error = function(e){
            print(paste0('Error running CiteSeqDimRedux.Dist for: ', datasetId))
            print(conditionMessage(e))
            traceback()
        })
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}