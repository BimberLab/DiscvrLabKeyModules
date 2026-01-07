for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  toDrop <- grep(names(seuratObj@meta.data), pattern = "sPLS", value = TRUE)
  if (length(toDrop) > 0) {
    print(paste0('Dropping pre-existing columns: ', paste0(toDrop, collapse = ', ')))
    for (colName in toDrop) {
      seuratObj[[toDrop]] <- NULL
    }
  }

  if (! 'TRB_Segments' %in% names(seuratObj@meta.data)) {
    print('Re-running AppendTcr to add segment columns')
    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = TRUE)
  }

  seuratObj <- RIRA::PredictTcellActivation(seuratObj)

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}