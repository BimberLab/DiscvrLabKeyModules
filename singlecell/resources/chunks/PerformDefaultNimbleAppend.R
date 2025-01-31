netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
  print(list.files(Sys.getenv('USER_HOME')))
  stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

# NOTE: this file is created by DownloadAndAppendNimble if there was an error. It might exist if a job failed and then was restarted
if (file.exists('debug.nimble.txt.gz')) {
  unlink('debug.nimble.txt.gz')
}

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  seuratObj <- Rdiscvr::PerformDefaultNimbleAppend(seuratObj)

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}