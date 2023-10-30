if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
   nThreads <- Sys.getenv('SEURAT_MAX_THREADS')
}

sdaFiles <- data.frame(DatasetId = character(), FileName = character())

 for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (!is.null(maxAllowableCells) && maxAllowableCells > 0 && ncol(seuratObj) > maxAllowableCells) {
       stop(paste0('The object has ', ncol(seuratObj), ' which is above the maxAllowableCells: ', maxAllowableCells))
    }

    outputFolder <- paste0('sdaOutput.', datasetId)
    if (dir.exists(outputFolder)) {
       unlink(outputFolder, recursive = TRUE)
    }

    sdaResults <- bindArgs(CellMembrane::RunSDA, seuratObj)()

    outputFileId <- ifelse(datasetId %in% names(datasetIdToOutputFileId), yes = datasetIdToOutputFileId[[datasetId]], no = NA)
    sdaResults$OutputFileId <- outputFileId

    if (!all(is.null(fieldNames))) {
       CellMembrane::PlotSdaCellScores(sdaResults = sdaResults, seuratObj = seuratObj, fieldNames = fieldNames)

       for (fieldName in fieldNames) {
          CellMembrane::Plot_SDAScoresPerFeature(seuratObj = seuratObj, sdaResults = sdaResults, metadataFeature = fieldName)
       }
    }

    fileName <- paste0('sda.', datasetId, '.rds')
    saveRDS(sdaResults, file = fileName)

    sdaFiles <- rbind(sdaFiles, data.frame(DatasetId = datasetId, FileName = fileName))

    # Cleanup
    rm(seuratObj)
    gc()
}

write.table(sdaFiles, file = 'sdaFiles.txt', row.names = FALSE, col.names = FALSE, quote = FALSE, sep = '\t')