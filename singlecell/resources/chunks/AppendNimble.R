if (!file.exists('/homeDir/.netrc')) {
  print(list.files('/homeDir'))
  stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  for (genomeId in names(nimbleGenomes)) {
    seuratObj <- Rdiscvr::DownloadAndAppendNimble(seuratObject = seuratObj, allowableGenomes = genomeId, targetAssayName = nimbleGenomes[[genomeId]], enforceUniqueFeatureNames = TRUE, dropAmbiguousFeatures = !retainAmbiguousFeatures)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}