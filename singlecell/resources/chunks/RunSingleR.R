for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!is.na(Sys.getenv('SEQUENCEANALYSIS_MAX_THREADS', unset = NA))) {
        nThreads <- Sys.getenv('SEQUENCEANALYSIS_MAX_THREADS')
    }

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}