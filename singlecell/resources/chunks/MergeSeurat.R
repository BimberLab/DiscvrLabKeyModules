doDiet <- exists('doDiet') && doDiet
disableAutoDietSeurat <- exists('disableAutoDietSeurat') && disableAutoDietSeurat
if (!doDiet && length(seuratObjects) > 20 && !disableAutoDietSeurat) {
    logger::log_info('More than 20 objects are being merged, turning on DietSeurat')
    doDiet <- TRUE
}

mergeBatch <- function(dat) {
    toMerge <- list()
    for (datasetId in names(dat)) {
        print(paste0('Loading: ', datasetId))
        if (doDiet) {
            toMerge[[datasetId]] <- Seurat::DietSeurat(readSeuratRDS(dat[[datasetId]]))
            gc()
        } else {
            toMerge[[datasetId]] <- readSeuratRDS(dat[[datasetId]])
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

    seuratObj <- CellMembrane::MergeSeuratObjs(toMerge, projectName = projectName, doGC = doDiet, errorOnBarcodeSuffix = errorOnBarcodeSuffix)
    return(seuratObj)
}

if (length(seuratObjects) == 1) {
    print('There is only one seurat object, no need to merge')
    datasetId <- names(seuratObjects)[[1]]
    saveData(readSeuratRDS(seuratObjects[[datasetId]]), datasetId)
} else {
    batchSize <- 20
    numBatches <- ceiling(length(seuratObjects) / batchSize)
    mergedObjectFiles <- list()
    for (i in 1:numBatches) {
        logger::log_info(paste0('Merging batch ', i, ' of ', numBatches))
        start <- 1 + (i-1)*batchSize
        end <- min(start+batchSize-1, length(seuratObjects))
        logger::log_info(paste0('processing: ', start, ' to ', end, ' of ', length(seuratObjects)))

        fn <- paste0('mergeBatch.', i, '.rds')
        saveRDS(mergeBatch(seuratObjects[start:end]), file = fn)
        mergedObjectFiles[[i]] <- fn

        logger::log_info(paste0('mem used: ', R.utils::hsize(as.numeric(pryr::mem_used()))))
        gc()
        logger::log_info(paste0('after gc: ', R.utils::hsize(as.numeric(pryr::mem_used()))))
    }

    logger::log_info('Done with batches')
    if (length(mergedObjectFiles) == 1) {
        seuratObj <- readRDS(mergedObjectFiles[[1]])
        unlink(mergedObjectFiles[[1]])
    } else {
        logger::log_info('performing final merge')
        seuratObj <- readRDS(mergedObjectFiles[[1]])
        unlink(mergedObjectFiles[[1]])

        for (i in 2:length(mergedObjectFiles)) {
            logger::log_info(paste0('Merging final file ', i, ' of ', length(mergedObjectFiles)))
            seuratObj <- merge(x = seuratObj, y = readRDS(mergedObjectFiles[[i]]), project = seuratObj@project.name)
            if (HasSplitLayers(seuratObj)) {
                seuratObj <- MergeSplitLayers(seuratObj)
            }

            unlink(mergedObjectFiles[[i]])

            logger::log_info(paste0('mem used: ', R.utils::hsize(as.numeric(pryr::mem_used()))))
            logger::log_info(paste0('seurat object: ', R.utils::hsize(as.numeric(utils::object.size(seuratObj)))))
            gc()
            logger::log_info(paste0('after gc: ', R.utils::hsize(as.numeric(pryr::mem_used()))))
        }
    }

    gc()

    saveData(seuratObj, projectName)
}