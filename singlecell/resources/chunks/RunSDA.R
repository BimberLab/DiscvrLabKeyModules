if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
   nThreads <- Sys.getenv('SEURAT_MAX_THREADS')
}

 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])
    gc()

    outputFolder <- ''
    if (dir.exists(outputFolder)) {
       unlink(outputFolder, recursive = TRUE)
    }

    sdaResults <- bindArgs(CellMembrane::RunSDA, seuratObj)()

    # Cleanup
    rm(seuratObj)
    gc()
}