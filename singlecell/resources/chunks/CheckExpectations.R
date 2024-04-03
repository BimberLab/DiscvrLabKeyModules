CheckField <- function(seuratObj, datasetId, fieldName, errorOnNA = TRUE) {
  if (!fieldName %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0(paste0('Missing ', fieldName, ' for dataset: ', datasetId)))
  }

  if (errorOnNA && any(is.na(seuratObj@meta.data[[fieldName]]))) {
    addErrorMessage(paste0(paste0('NA values found for ', fieldName, ' for dataset: ', datasetId)))
  }
}

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  if (requireSingleDatasetInput && length(unique(seuratObj$DatasetId)) > 1) {
    addErrorMessage(paste0('Seurat data prototypes must be a single dataset. Problem ID: ', datasetId))
  }

  if (is.null(usesHashing[[datasetId]])) {
    addErrorMessage(paste0('No hashing context provided for: ', datasetId))
    break
  }

  if (usesHashing[[datasetId]] && requireHashing) {
    if (!('HTO.Classification' %in% names(seuratObj@meta.data))) {
      addErrorMessage(paste0('Missing cell hashing calls for dataset: ', datasetId))
    }
  }

  if (is.null(usesCiteSeq[[datasetId]])) {
    addErrorMessage(paste0('No CITE-seq context provided for: ', datasetId))
    break
  }

  if (usesCiteSeq[[datasetId]] && requireCiteSeq) {
    if (!'ADT' %in% names(seuratObj@assays)) {
      addErrorMessage(paste0('Missing ADT data for dataset: ', datasetId))
    }
  }

  if (requireSaturation) {
    CheckField(seuratObj, datasetId, 'Saturation.RNA')
  }

  if (requireSingleR) {
    CheckField(seuratObj, datasetId, 'SingleRConsensus')
  }

  if (requireScGate) {
    CheckField(seuratObj, datasetId, 'scGateConsensus', errorOnNA = FALSE)
  }

  if (requireRiraImmune) {
    CheckField(seuratObj, datasetId, 'RIRA_Immune_v2.cellclass')
  }

  if (length(errorMessages) > 0) {
    break
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}