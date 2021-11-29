# Binds arguments from the environment to the target function
bindArgs <- function(fun, seuratObj, allowableArgNames = NULL, disallowedArgNames = NULL) {
    boundArgs <- list()
    boundArgs[['seuratObj']] <- seuratObj

    for (name in names(formals(fun))) {
        if (!is.null(disallowedArgNames) && (name %in% disallowedArgNames)) {
            next
        }
        else if (name %in% names(boundArgs)) {
            next
        }
        else if (exists(name)) {
            if (!is.null(allowableArgNames) && !(name %in% allowableArgNames)) {
                next
            }

            val <- get(name)
            displayVal <- val
            if (all(is.null(val))) {
                displayVal <- 'NULL'
            } else if (all(is.na(val))) {
                displayVal <- 'NA'
            } else if (is.object(val)) {
                displayVal <- '[object]'
            }

            if (length(displayVal) > 1) {
                displayVal <- paste0(displayVal, collapse = ',')
            }

            print(paste0('Binding argument: ', name, ': ', displayVal))
            boundArgs[[name]] <- val
        }
    }

    formals(fun)[names(boundArgs)] <- boundArgs

    fun
}

savedFiles <- data.frame(datasetId = character(), datasetName = character(), filename = character(), outputFileId = character(), readsetId = character())
write.table(savedFiles, file = 'savedSeuratObjects.txt', quote = FALSE, sep = '\t', row.names = FALSE, col.names = FALSE)

saveData <- function(seuratObj, datasetId) {
    print(paste0('Saving dataset: ', datasetId))
    print(seuratObj)

    fn <- paste0(outputPrefix, '.', datasetId, '.seurat.rds')
    barcodeFile <- paste0(outputPrefix, '.', datasetId, '.cellBarcodes.csv')
    metaFile <- paste0(outputPrefix, '.', datasetId, '.seurat.meta.txt')

    saveRDS(seuratObj, file = fn)

    datasetName <- ifelse(datasetId %in% names(datasetIdToName), yes = datasetIdToName[[datasetId]], no = datasetId)

    # NOTE: this is the ID of the original loupe file. Needed for operations like appending cell hashing or CITE-seq
    outputFileId <- ifelse(datasetId %in% names(datasetIdToOutputFileId), yes = datasetIdToOutputFileId[[datasetId]], no = NA)

    readsetId <- ifelse(datasetId %in% names(datasetIdToReadset), yes = datasetIdToReadset[[datasetId]], no = NA)

    toAppend <- data.frame(datasetId = datasetId, datasetName = datasetName, filename = fn, outputFileId = outputFileId, readsetId = readsetId)
    write.table(toAppend, file = 'savedSeuratObjects.txt', quote = FALSE, sep = '\t', row.names = FALSE, col.names = FALSE, append = TRUE)

    # Write cell barcodes and metadata:
    metaDf <- seuratObj@meta.data
    metaDf$cellbarcode <- colnames(seuratObj)
    write.table(metaDf, file = metaFile, quote = F, row.names = F, sep = ',', col.names = T)
    write.table(data.frame(CellBarcode = colnames(seuratObj)), file = barcodeFile, quote = F, row.names = F, sep = ',', col.names = F)
}

intermediateFiles <- c()
addIntermediateFile <- function(f) { intermediateFiles <<- c(intermediateFiles, f) }

print('Updating future.globals.maxSize')
options(future.globals.maxSize = Inf)

options('Seurat.memsafe' = TRUE)