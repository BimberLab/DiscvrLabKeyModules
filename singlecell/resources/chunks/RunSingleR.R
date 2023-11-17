# Added to avoid celldex/ExperimentHub/BiocFileCache write errors
Sys.setenv('HOME', '/dockerHomeDir')

 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}