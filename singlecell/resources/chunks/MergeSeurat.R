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
    mergedObjects <- list()
    for (i in 1:numBatches) {
        message(paste0('Merging batch ', i, ' of ', numBatches))
        start <- 1 + (i-1)*batchSize
        end <- min(start+batchSize-1, length(seuratObjects))
        print(paste0('processing: ', start, ' to ', end, ' of ', length(seuratObjects)))

        mergedObjects[[i]] <- mergeBatch(seuratObjects[start:end])
        gc()
    }

    print('Done with batches')
    if (length(mergedObjects) == 1) {
        seuratObj <- mergedObjects[[1]]
    } else {
        message('performing final merge')
        seuratObj <- merge(x = mergedObjects[[1]], y = mergedObjects[2:length(mergedObjects)], project = mergedObjects[[1]]@project.name)
        seuratObj <- CellMembrane::MergeSeuratObjs(mergedObjects, projectName = mergedObjects[[1]]@project.name, doGC = doDiet, errorOnBarcodeSuffix = errorOnBarcodeSuffix)
        if (HasSplitLayers(seuratObj)) {
            seuratObj <- MergeSplitLayers(seuratObj)
        }
    }

    rm(mergedObjects)
    gc()

    saveData(seuratObj, projectName)
}