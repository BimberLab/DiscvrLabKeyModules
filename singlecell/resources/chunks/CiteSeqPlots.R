for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        for (adt in rownames(seuratObj[['ADT']])) {
            tryCatch({
                CellMembrane::FeaturePlotAcrossReductions(seuratObj, features = paste0('adt_', adt))
            }, error = function(e){
                print(conditionMessage(e))
                traceback()
                print('ADTs:')
                print(sort(rownames(seuratObj@assays$ADT)))
                stop(paste0('Error running FeaturePlotAcrossReductions for: ', datasetId))
            })

        }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}