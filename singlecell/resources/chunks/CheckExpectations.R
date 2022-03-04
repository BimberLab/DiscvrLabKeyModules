for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

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

  if (requireSaturation && !'Saturation.RNA' %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0('Missing per-cell RNA saturation data for dataset: ', datasetId))
  }

  if (requireSingleR && !'SingleRConsensus' %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0('Missing SingleRConsensus label for dataset: ', datasetId))
  }

  if (requireScGate && !'scGateConsensus' %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0('Missing scGateConsensus label for dataset: ', datasetId))
  }

  if (length(errorMessages) > 0) {
    break
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}