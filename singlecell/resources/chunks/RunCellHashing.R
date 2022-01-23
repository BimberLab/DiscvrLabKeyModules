for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    for (id in unique(seuratObj$DatasetId)) {
        if (!(id %in% names(featureData))) {
            stop(paste0('No hashing information found for datasetId: ', id))
        }

        callFile <- featureData[[id]]
        if (!is.null(callFile)) {
            seuratObj <- cellhashR::AppendCellHashing(seuratObj, barcodeCallFile = callFile, barcodePrefix = id)

            fractionFailedHashing <- 1 - (sum(seuratObj@meta.data$HTO.Classification %in% c('Singlet', 'Doublet')) / nrow(seuratObj@meta.data))
            if (!is.null(maxHashingPctFail) && fractionFailedHashing > maxHashingPctFail) {
                stop(paste0('Fraction failing cell hashing was : ', fractionFailedHashing, ' for dataset: ', datasetId, ', above threshold of: ', maxHashingPctFail))
            }

            fractionDiscordantHashing <- 1 - (sum(seuratObj@meta.data$HTO.Classification == 'Discordant') / nrow(seuratObj@meta.data))
            if (!is.null(maxHashingPctDiscordant) && fractionDiscordantHashing > maxHashingPctDiscordant) {
                stop(paste0('Discordant hashing rate was: ', fractionDiscordantHashing, ' for dataset: ', datasetId, ', above threshold of: ', maxHashingPctDiscordant))
            }

        } else {
            # Add empty columns to keep objects consistent
            if (!'HTO' %in% names(seuratObj@meta.data)) {
                seuratObj$HTO <- c('NotUsed')
            } else {
                seuratObj$HTO <- as.character(seuratObj$HTO)
                seuratObj$HTO[seuratObj$DatasetId == id] <- 'NotUsed'
                seuratObj$HTO <- naturalsort::naturalfactor(seuratObj$HTO)
            }
            
            if (!'HTO.Classification' %in% names(seuratObj@meta.data)) {
                seuratObj$HTO.Classification <- c('NotUsed')
            } else {
                seuratObj$HTO.Classification <- as.character(seuratObj$HTO.Classification)
                seuratObj$HTO.Classification[seuratObj$DatasetId == id] <- 'NotUsed'
                seuratObj$HTO.Classification <- naturalsort::naturalfactor(seuratObj$HTO.Classification)
            }
        }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}