for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!is.na(Sys.getenv('SEQUENCEANALYSIS_MAX_THREADS', unset = NA))) {
        nThreads <- Sys.getenv('SEQUENCEANALYSIS_MAX_THREADS')
    }

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}