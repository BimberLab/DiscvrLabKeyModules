netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
  print(list.files(Sys.getenv('USER_HOME')))
  stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  if (! 'TRB_WithProductive' %in% names(seuratObj@meta.data)) {
    print('Re-running AppendTcr to add segment columns')
    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = TRUE)
  }

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