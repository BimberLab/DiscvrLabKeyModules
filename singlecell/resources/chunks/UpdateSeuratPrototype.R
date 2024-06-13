if (!file.exists('/homeDir/.netrc')) {
  print(list.files('/homeDir'))
  stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  if (reapplyMetadata) {
    seuratObj <- Rdiscvr::QueryAndApplyCdnaMetadata(seuratObj)
  }

  if (runRira) {
    seuratObj <- RIRA::Classify_ImmuneCells(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    seuratObj <- RIRA::Classify_Myeloid(seuratObj, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
  }

  if (applyTCR) {
    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = allowMissingTcr)
  }

  if (runTNKClassification) {
    # ClassifyTNKByExpression will fail without this, so ignore allowMissingTcr
    if (!'HasCDR3Data' %in% names(seuratObj@meta.data)) {
      seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj)
    }

    seuratObj <- Rdiscvr::ClassifyTNKByExpression(seuratObj)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}