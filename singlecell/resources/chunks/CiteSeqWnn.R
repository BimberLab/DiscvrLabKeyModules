for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    tryCatch({
        if (!(assayName %in% names(seuratObj@assays))) {
            print(paste0(assayName, ' assay not present, skipping'))
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