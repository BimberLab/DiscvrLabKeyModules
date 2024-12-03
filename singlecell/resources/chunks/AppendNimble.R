netRc <- paste0(Sys.getEnv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
  print(list.files(Sys.getEnv('USER_HOME')))
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

  for (genomeId in names(nimbleGenomes)) {
    maxAmbiguityAllowed <- !nimbleGenomeAmbiguousPreference[[genomeId]]
    seuratObj <- Rdiscvr::DownloadAndAppendNimble(seuratObject = seuratObj, allowableGenomes = genomeId, ensureSamplesShareAllGenomes = ensureSamplesShareAllGenomes, targetAssayName = nimbleGenomes[[genomeId]], enforceUniqueFeatureNames = TRUE, maxAmbiguityAllowed = maxAmbiguityAllowed, maxLibrarySizeRatio = maxLibrarySizeRatio)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}