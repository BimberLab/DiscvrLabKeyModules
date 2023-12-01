# Added to avoid celldex/ExperimentHub/BiocFileCache write errors
cacheDir <- '/BiocFileCache/.cache'
if (dir.exists(cacheDir)) {
   unlink(cacheDir, recursive = TRUE)
}

dir.create(cacheDir, recursive=TRUE)
ExperimentHub::setExperimentHubOption('cache', cacheDir)
library(ExperimentHub)

 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (singleRSpecies == 'mouse') {
       datasets <- 'MouseRNAseqData'
    }
    else {
       print('Using human singleR datasets')
    }

    seuratObj <- bindArgs(CellMembrane::RunSingleR, seuratObj, disallowedArgNames = c('assay'))()

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}