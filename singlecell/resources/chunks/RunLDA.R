if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
   nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
   nCores <- 1
}

ldaFiles <- data.frame(DatasetId = character(), FileName = character())

 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!is.null(maxAllowableCells) && maxAllowableCells > 0 && ncol(seuratObj) > maxAllowableCells) {
       stop(paste0('The object has ', ncol(seuratObj), ' which is above the maxAllowableCells: ', maxAllowableCells))
    }

    ldaResults <- bindArgs(CellMembrane::RunLDA, seuratObj)()

    outputFileId <- ifelse(datasetId %in% names(datasetIdToOutputFileId), yes = datasetIdToOutputFileId[[datasetId]], no = NA)
    ldaResults$OutputFileId <- outputFileId

    fileName <- paste0('lda.', datasetId, '.rds')
    saveRDS(ldaResults, file = fileName)

    ldaFiles <- rbind(ldaFiles, data.frame(DatasetId = datasetId, FileName = fileName))

    # Cleanup
    rm(seuratObj)
    gc()
}

write.table(ldaFiles, file = 'ldaFiles.txt', row.names = FALSE, col.names = FALSE, quote = FALSE, sep = '\t')