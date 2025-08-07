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
  df <- Rdiscvr::CalculateAndStoreTcrRepertioreStats(seuratObj)

  # Cleanup
  rm(seuratObj)
  rm(df)
  gc()
}