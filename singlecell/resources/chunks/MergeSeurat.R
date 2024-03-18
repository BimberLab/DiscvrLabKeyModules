doDiet <- exists('doDiet') && doDiet

mergeBatch <- function(dat) {
    toMerge <- list()
    for (datasetId in names(dat)) {
        message(paste0('Loading: ', datasetId))
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
        message(paste0('Merging batch ', i, ' of ', numBatches))
        start <- 1 + (i-1)*batchSize
        end <- min(start+batchSize-1, length(seuratObjects))
        message(paste0('processing: ', start, ' to ', end, ' of ', length(seuratObjects)))

        fn <- paste0('mergeBatch.', i, '.rds')
        saveRDS(mergeBatch(seuratObjects[start:end]), file = fn)
        mergedObjectFiles[[i]] <- fn

        print('mem used:')
        print(pryr::mem_used())
        gc()
    }

    print('Done with batches')
    if (length(mergedObjectFiles) == 1) {
        seuratObj <- readRDS(mergedObjectFiles[[1]])
        unlink(mergedObjectFiles[[1]])
    } else {
        message('performing final merge')
        seuratObj <- readRDS(mergedObjectFiles[[1]])
        unlink(mergedObjectFiles[[1]])

        for (i in 2:length(mergedObjectFiles)) {
            print(paste0('Merging final file ', i, ' of ', length(mergedObjectFiles)))
            seuratObj <- merge(x = seuratObj, y = readRDS(mergedObjectFiles[[i]]), project = seuratObj@project.name)
            if (HasSplitLayers(seuratObj)) {
                seuratObj <- MergeSplitLayers(seuratObj)
            }

            unlink(mergedObjectFiles[[i]])

            print('mem used:')
            print(pryr::mem_used())
            gc()
        }
    }

    gc()

    saveData(seuratObj, projectName)
}