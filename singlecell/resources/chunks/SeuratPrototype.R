metricData <- data.frame(dataId = integer(), readsetId = integer(), metricname = character(), metricvalue = numeric())

for (datasetId in names(seuratObjects)) {
  seuratObj <- readRDS(seuratObjects[[datasetId]])
  metricData <- rbind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'TotalCells', metricvalue = ncol(seuratObj)))

  if (length(unique(seuratObj$DatasetId)) > 1) {
    stop(paste0('Seurat data prototypes must be a single dataset. Problem ID: ', datasetId))
  }

  if (is.null(usesHashing[[datasetId]])) {
    stop(paste0('No hashing context provided for: ', datasetId))
  }

  if (usesHashing[[datasetId]]) {
    if (requireHashing && !('HTO.Classification' %in% names(seuratObj@meta.data))) {
      stop(paste0('Missing cell hashing calls for dataset: ', datasetId))
    }

    hashingCalled <- sum(seuratObj@meta.data$HTO.Classification %in% c('Singlet', 'Doublet'))
    hashingCalled <- hashingCalled / nrow(seuratObj@meta.data)
    hashingCalled <- 1 - hashingCalled

    if (!is.null(maxHashingPctFail) && hashingCalled < maxHashingPctFail) {
        stop(paste0('Hashing call rate was: ', hashingCalled, ' for dataset: ', datasetId))
    }

    metricData <- bind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionFailedHashing', metricvalue = hashingCalled))

    discordantCells <- sum(seuratObj@meta.data$HTO.Classification == 'Discordant')
    discordantCells <- discordantCells / nrow(seuratObj@meta.data)
    discordantCells <- 1 - discordantCells

    if (!is.null(maxHashingPctDiscordant) && discordantCells < maxHashingPctDiscordant) {
        stop(paste0('Discordant hashing rate was: ', discordantCells, ' for dataset: ', datasetId))
    }

    metricData <- bind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionDiscordantHashing', metricvalue = discordantCells))
  }

  if (is.null(usesCiteSeq[[datasetId]])) {
    stop(paste0('No CITE-seq context provided for: ', datasetId))
  }

  if (usesCiteSeq[[datasetId]] && requireCiteSeq) {
    if (!'ADT' %in% names(seuratObj@assays)) {
      stop(paste0('Missing ADT data for dataset: ', datasetId))
    }

    fractionADTGT0 <- sum(seuratObj@meta.data$nCount_ADT > 0) / nrow(seuratObj@meta.data)
    metricData <- bind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionADT_GT0', metricvalue = fractionADTGT0))

    fractionADTGT5 <- sum(seuratObj@meta.data$nCount_ADT > 5) / nrow(seuratObj@meta.data)
    metricData <- bind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'FractionADT_GT5', metricvalue = fractionADTGT5))
  }

  if (requireSaturation && !'Saturation.RNA' %in% names(seuratObj@meta.data)) {
    stop(paste0('Missing per-cell RNA saturation data for dataset: ', datasetId))
  }

  if (!is.null(minSaturation)) {
    if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
      stop(paste0('Min saturation provided, but missing per-cell RNA saturation data for dataset: ', datasetId))
    }
  }

  if ('Saturation.RNA' %in% names(seuratObj@meta.data)) {
    meanSaturation.RNA <- mean(seuratObj$Saturation.RNA)
    if (!is.null(minSaturation) && meanSaturation.RNA < minSaturation) {
      stop(paste0('Mean RNA saturation was: ', meanSaturation.RNA, ' for dataset: ', datasetId))
    }

    metricData <- bind(metricData, data.frame(dataId = datasetId, readsetId = datasetIdToReadset[[datasetId]], metricname = 'MeanSaturation.RNA', metricvalue = meanSaturation.RNA))
  }

  if (requireSingleR && !'dice.label' %in% names(seuratObj@meta.data)) {
    stop(paste0('Missing SingleR DICE labels for dataset: ', datasetId))
  }

  if (dietSeurat) {
    print(paste0('Running DietSeurat for: ', datasetId))
    seuratObj <- DietSeurat(seuratObj)
  }

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}

if (nrow(metricData) > 0) {
  write.table(metricData, file = 'seurat.metrics.txt', sep = '\t', quote = F, row.names = F, col.names = F)
}