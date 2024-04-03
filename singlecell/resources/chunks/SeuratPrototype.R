CheckField <- function(seuratObj, datasetId, fieldName, errorOnNA = TRUE) {
  if (!fieldName %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0(paste0('Missing ', fieldName, ' for dataset: ', datasetId)))
  }

  if (errorOnNA && any(is.na(seuratObj@meta.data[[fieldName]]))) {
    addErrorMessage(paste0(paste0('NA values found for ', fieldName, ' for dataset: ', datasetId)))
  }
}

metricData <- data.frame(dataId = integer(), readsetId = integer(), metricname = character(), metricvalue = numeric())

for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])
  metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'TotalCells', metricvalue = ncol(seuratObj)))

  if (length(unique(seuratObj$DatasetId)) > 1) {
    addErrorMessage(paste0('Seurat data prototypes must be a single dataset. Problem ID: ', datasetId))
  }

  if (is.null(usesHashing[[datasetId]])) {
    addErrorMessage(paste0('No hashing context provided for: ', datasetId))
    break
  }

  if (usesHashing[[datasetId]]) {
    if (requireHashing && !('HTO.Classification' %in% names(seuratObj@meta.data))) {
      addErrorMessage(paste0('Missing cell hashing calls for dataset: ', datasetId))
    }

    fractionSinglet <- (sum(seuratObj@meta.data$HTO.Classification %in% c('Singlet', 'NotUsed')) / nrow(seuratObj@meta.data))
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionSinglet', metricvalue = fractionSinglet))

    fractionDoublet <- (sum(seuratObj@meta.data$HTO.Classification == 'Doublet') / nrow(seuratObj@meta.data))
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionDoublet', metricvalue = fractionDoublet))

    fractionFailedHashing <- 1 - (sum(seuratObj@meta.data$HTO.Classification %in% c('Singlet', 'Doublet')) / nrow(seuratObj@meta.data))
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionFailedHashing', metricvalue = fractionFailedHashing))

    fractionDiscordantHashing <- sum(seuratObj@meta.data$HTO.Classification == 'Discordant') / nrow(seuratObj@meta.data)
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionDiscordantHashing', metricvalue = fractionDiscordantHashing))
  } else {
    # Ensure these fields exist:
    seuratObj$HTO.Classification <- c('NotUsed')
    seuratObj$HTO <- c('NotUsed')
  }

  if (is.null(usesCiteSeq[[datasetId]])) {
    addErrorMessage(paste0('No CITE-seq context provided for: ', datasetId))
    break
  }

  if (usesCiteSeq[[datasetId]] && requireCiteSeq) {
    if (!'ADT' %in% names(seuratObj@assays)) {
      addErrorMessage(paste0('Missing ADT data for dataset: ', datasetId))
    }

    fractionADTGT0 <- sum(seuratObj@meta.data$nCount_ADT > 0) / nrow(seuratObj@meta.data)
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionADT_GT0', metricvalue = fractionADTGT0))

    fractionADTGT5 <- sum(seuratObj@meta.data$nCount_ADT > 5) / nrow(seuratObj@meta.data)
    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionADT_GT5', metricvalue = fractionADTGT5))
  }

  if (requireSaturation && !'Saturation.RNA' %in% names(seuratObj@meta.data)) {
    addErrorMessage(paste0('Missing per-cell RNA saturation data for dataset: ', datasetId))
  }

  if (!is.null(minSaturation)) {
    if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
      addErrorMessage(paste0('Min saturation provided, but missing per-cell RNA saturation data for dataset: ', datasetId))
    }
  }

  if ('Saturation.RNA' %in% names(seuratObj@meta.data)) {
    meanSaturation.RNA <- mean(seuratObj$Saturation.RNA)
    if (!is.null(minSaturation) && meanSaturation.RNA < minSaturation) {
      addErrorMessage(paste0('Mean RNA saturation was: ', meanSaturation.RNA, ' for dataset: ', datasetId, ', below threshold of: ', minSaturation, ', total cells: ', ncol(seuratObj)))
    }

    metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'MeanSaturation.RNA', metricvalue = meanSaturation.RNA))
  }

  if (requireSingleR) {
    CheckField(seuratObj, datasetId, 'SingleRConsensus')
  }

  if (requireScGate) {
    CheckField(seuratObj, datasetId, 'scGateConsensus', errorOnNA = FALSE)
  }

  if (length(errorMessages) > 0) {
    break
  }

  if (dietSeurat) {
    print(paste0('Running DietSeurat for: ', datasetId))
    seuratObj <- Seurat::DietSeurat(seuratObj)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}

if (nrow(metricData) > 0) {
  write.table(metricData, file = 'seurat.metrics.txt', sep = '\t', quote = F, row.names = F, col.names = F)
}