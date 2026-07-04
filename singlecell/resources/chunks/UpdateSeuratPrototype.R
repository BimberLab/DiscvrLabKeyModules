netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
  print(list.files(Sys.getenv('USER_HOME')))
  stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
  nCores <- Sys.getenv('SEURAT_MAX_THREADS')
} else {
  nCores <- 1
}

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  if (reapplyMetadata) {
    seuratObj <- Rdiscvr::QueryAndApplyCdnaMetadata(seuratObj)
  }

  if (applyTCR) {
    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = allowMissingTcr)
  }

  if (runRira) {
    seuratObj <- RIRA::Classify_ImmuneCells(seuratObj, maxBatchSize = 500000, retainProbabilityMatrix = FALSE, maxAllowedUnknown = maxAllowedUnknown, filterDisallowedClasses = filterDisallowedClasses)
    seuratObj <- RIRA::Classify_TNK(seuratObj, maxBatchSize = 500000, retainProbabilityMatrix = FALSE)
    seuratObj <- RIRA::Classify_Myeloid(seuratObj, maxBatchSize = 500000, retainProbabilityMatrix = FALSE)
  }

  if (runTNKClassification) {
    # ClassifyTNKByExpression will fail without this, so ignore allowMissingTcr
    if (!'HasCDR3Data' %in% names(seuratObj@meta.data)) {
      seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj)
    }

    seuratObj <- Rdiscvr::ClassifyTNKByExpression(seuratObj)
  }

  if (saveRepertoireStats) {
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])
    outputFile <- gsub(seuratObjects[[datasetId]], pattern = '.rds', replacement = '.tcrStats.txt')
    df <- Rdiscvr::CalculateAndStoreTcrRepertoireStats(seuratObj, outputFile = outputFile)
  }

  if (scoreActivation) {
    # Drop existing columns:
    toDrop <- grep(names(seuratObj@meta.data), pattern = "sPLS", value = TRUE)
    if (length(toDrop) > 0) {
      print(paste0('Dropping pre-existing columns: ', paste0(toDrop, collapse = ', ')))
      for (colName in toDrop) {
        seuratObj[[toDrop]] <- NULL
      }
    }

    seuratObj <- RIRA::PredictTcellActivation(seuratObj)
  }

  if (recalculateUCells) {
    seuratObj <- RIRA::CalculateUCellScores(seuratObj, storeRanks = FALSE, assayName = 'RNA', forceRecalculate = TRUE, ncores = nCores, dropAllExistingUcells = TRUE)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}