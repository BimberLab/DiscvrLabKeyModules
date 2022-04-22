for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    tryCatch({
        if (!('ADT' %in% names(seuratObj@assays))) {
            print('ADT assay not present, skipping')
        } else {
            seuratObj <- bindArgs(CellMembrane::RunSeuratWnn, seuratObj)()
        }
    }, error = function(e){
        print(paste0('Error running RunSeuratWnn for: ', datasetId))
        print(conditionMessage(e))
        traceback()
    })

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}