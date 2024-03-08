if (!file.exists('/homeDir/.netrc')) {
  print(list.files('/homeDir'))
  stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

# NOTE: this file is created by DownloadAndAppendNimble if there was an error. It might exist if a job failed and then was restarted
if (file.exists('debug.nimble.txt')) {
  unlink('debug.nimble.txt')
}

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  for (genomeId in names(nimbleGenomes)) {
    seuratObj <- Rdiscvr::DownloadAndAppendNimble(seuratObject = seuratObj, allowableGenomes = genomeId, ensureSamplesShareAllGenomes = ensureSamplesShareAllGenomes, targetAssayName = nimbleGenomes[[genomeId]], enforceUniqueFeatureNames = TRUE, dropAmbiguousFeatures = !retainAmbiguousFeatures, maxLibrarySizeRatio = maxLibrarySizeRatio)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}