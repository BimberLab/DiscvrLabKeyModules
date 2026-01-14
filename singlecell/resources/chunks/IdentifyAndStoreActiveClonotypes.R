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

  if (! 'TRB_Segments' %in% names(seuratObj@meta.data)) {
    print('Re-running AppendTcr to add segment columns')
    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = TRUE)
  }

  Rdiscvr::IdentifyAndStoreActiveClonotypes(seuratObj, chain = 'TRA', storeStimLevelData = FALSE)
  Rdiscvr::IdentifyAndStoreActiveClonotypes(seuratObj, chain = 'TRB')

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}