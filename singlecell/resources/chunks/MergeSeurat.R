doDiet <- exists('doDiet') && doDiet
disableAutoDietSeurat <- exists('disableAutoDietSeurat') && disableAutoDietSeurat
if (!doDiet && length(seuratObjects) > 20 && !disableAutoDietSeurat) {
    logger::log_info('More than 20 objects are being merged, turning on DietSeurat')
    doDiet <- TRUE
}

filesToDelete <- c()

mergeBatchInMemory <- function(datasetIdToFilePath, saveFile) {
    if (all(is.null(names(datasetIdToFilePath)))) {
        stop('No names provided on datasetIdToFilePath')
    }

    toMerge <- list()
    for (datasetId in names(datasetIdToFilePath)) {
        print(paste0('Loading: ', datasetId))
        if (doDiet) {
            toMerge[[datasetId]] <- Seurat::DietSeurat(readSeuratRDS(datasetIdToFilePath[[datasetId]]))
            gc()
        } else {
            toMerge[[datasetId]] <- readSeuratRDS(datasetIdToFilePath[[datasetId]])
        }

        if (ncol(toMerge[[datasetId]]) == 1) {
            logger::log_info(paste0('Dataset has single cell, skipping: ', datasetId))
            toMerge[[datasetId]] <- NULL
        }
    }

    if (!is.null(assaysToDrop)) {
        for (assayName in assaysToDrop) {
            print(paste0('Dropping assay: ', assayName))
            for (datasetId in names(toMerge)) {
                if (assayName %in% names(toMerge[[datasetId]]@assays)) {
                    toMerge[[datasetId]]@assays[[assayName]] <- NULL
                }
            }
        }
    }

    if (length(toMerge) == 0) {
        stop('There were no passing seurat objects!')
    }

    saveRDS(CellMembrane::MergeSeuratObjs(toMerge, projectName = projectName, doGC = doDiet, errorOnBarcodeSuffix = errorOnBarcodeSuffix, expectedDefaultAssay = expectedDefaultAssay), file = saveFile)
    filesToDelete <<- c(filesToDelete, saveFile)

    logger::log_info(paste0('mem used: ', R.utils::hsize(as.numeric(pryr::mem_used()))))
    gc()
    logger::log_info(paste0('after gc: ', R.utils::hsize(as.numeric(pryr::mem_used()))))

    return(saveFile)
}

if (is.null(maxAllowableInputFileSizeMb)) {
    logger::log_info('maxAllowableInputFileSizeMb not provided, defaulting to 200')
    maxAllowableInputFileSizeMb <- 200
}

mergeBatch <- function(seuratObjects, outerBatchIdx, maxBatchSize = 20, maxInputFileSizeMb = maxAllowableInputFileSizeMb) {
    logger::log_info(paste0('Beginning outer batch: ', outerBatchIdx, ' with total files: ', length(seuratObjects)))

    if (length(seuratObjects) == 1) {
        print('Single file, nothing to do')
        return(seuratObjects)
    }

    # Phase 1: group into batches:
    batchList <- list()
    activeBatch <- list()
    sizeOfBatch <- 0
    batchIdx <- 1
    for (datasetId in names(seuratObjects)) {
        activeBatch[[datasetId]] <- seuratObjects[[datasetId]]
        sizeInMb <- (file.size(seuratObjects[[datasetId]]) / 1024^2)
        sizeOfBatch <- sizeOfBatch + sizeInMb

        if (length(activeBatch) >= maxBatchSize || (sizeOfBatch >= maxInputFileSizeMb && length(activeBatch) > 1)) {
            logger::log_info(paste0('adding to batch with ', length(activeBatch), ' files and ', sizeOfBatch, ' MB'))
            batchList[[batchIdx]] <- activeBatch
            activeBatch <- list()
            sizeOfBatch <- 0
            batchIdx <- batchIdx + 1
            next
        }
    }

    # Account for final files:
    if (length(activeBatch) > 0) {
        logger::log_info(paste0('finalizing batch with ', length(activeBatch), ' files and ', sizeOfBatch, ' MB'))
        batchList[[batchIdx]] <- activeBatch
    }

    if (length(batchList) == 0){
        stop('Error: zero length batchList')
    }

    mergedObjectFiles <- list()
    for (i in 1:length(batchList)) {
        activeBatch <- batchList[[i]]
        logger::log_info(paste0('Merging inner batch ', i, ' of ', length(batchList), ' with ', length(activeBatch), ' files'))

        batchName <- paste0('merge.', outerBatchIdx, '.', i)
        saveFile <- paste0(batchName, '.rds')
        if (length(activeBatch) == 1){
            print('Only single file in batch, skipping merge')
            mergedObjectFiles[[batchName]] <- activeBatch[[1]]
            next
        }
        mergedObjectFiles[[batchName]] <- mergeBatchInMemory(activeBatch, saveFile = saveFile)
    }
    logger::log_info('Done with inner batch')

    if (length(mergedObjectFiles) > 1) {
        return(mergeBatch(mergedObjectFiles, outerBatchIdx = (outerBatchIdx + 1), maxInputFileSizeMb = maxInputFileSizeMb, maxBatchSize = maxBatchSize))
    }

    return(mergedObjectFiles)
}

mergedObjectFiles <- mergeBatch(seuratObjects, outerBatchIdx = 1)
if (length(mergedObjectFiles) != 1) {
    stop(paste0('Expected single file, found: ', length(mergedObjectFiles)))
}

print('Overall merge complete')
gc()

saveData(readRDS(mergedObjectFiles[[1]]), projectName)

# Cleanup:
for (fn in filesToDelete) {
    unlink(fn)
}