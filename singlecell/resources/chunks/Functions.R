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
            if (all(is.null(val))) {
                boundArgs[name] <- list(NULL)
            } else {
                boundArgs[[name]] <- val
            }
        }
    }

    formals(fun)[names(boundArgs)] <- boundArgs

    fun
}

clearSeuratCommands <- function(seuratObj, maxSize = 500000) {
    for (commandName in names(seuratObj@commands)) {
        val <- object.size(x = slot(seuratObj@commands[[commandName]], 'call.string'))
        if (val > maxSize) {
            print(paste0('Clearing call.string for: ', commandName, '. size: ', format(val, units = 'auto')))
            slot(seuratObj@commands[[commandName]], 'call.string') <- ''
        }
    }

    return(seuratObj)
}

printName <- function(datasetId) {
    datasetName <- ifelse(datasetId %in% names(datasetIdToName), yes = datasetIdToName[[datasetId]], no = datasetId)
    print(paste0('Processing dataset: ', datasetName))
}

savedFiles <- data.frame(datasetId = character(), datasetName = character(), filename = character(), outputFileId = character(), readsetId = character())
if (file.exists('/work/savedSeuratObjects.txt')) {
    print('Deleting pre-existing savedSeuratObjects.txt file')
    unlink('/work/savedSeuratObjects.txt')
}

file.create('/work/savedSeuratObjects.txt')
print(paste0('Total lines in savedSeuratObjects.txt on job start:', length(readLines('savedSeuratObjects.txt'))))

saveData <- function(seuratObj, datasetId) {
    message(paste0('Saving dataset: ', datasetId, ' with ', ncol(seuratObj), ' cells'))
    print(paste0('Saving dataset: ', datasetId))
    print(seuratObj)

    datasetIdForFile <- makeLegalFileName(datasetId)
    fn <- paste0(outputPrefix, '.', datasetIdForFile, '.seurat.rds')
    message(paste0('Filename: ', fn))

    message(paste0('Saving RDS file: ', fn, ' with ', ncol(seuratObj), ' cells'))
    barcodeFile <- paste0(outputPrefix, '.', datasetIdForFile, '.cellBarcodes.csv')
    metaFile <- paste0(outputPrefix, '.', datasetIdForFile, '.seurat.meta.txt')

    saveRDS(seuratObj, file = fn)

    datasetName <- ifelse(datasetId %in% names(datasetIdToName), yes = datasetIdToName[[datasetId]], no = datasetId)

    # NOTE: this is the ID of the original loupe file. Needed for operations like appending cell hashing or CITE-seq
    outputFileId <- ifelse(datasetId %in% names(datasetIdToOutputFileId), yes = datasetIdToOutputFileId[[datasetId]], no = NA)

    readsetId <- ifelse(datasetId %in% names(datasetIdToReadset), yes = datasetIdToReadset[[datasetId]], no = NA)
    print(paste0('readsetId: ', readsetId))

    toAppend <- data.frame(datasetId = datasetId, datasetName = datasetName, filename = fn, outputFileId = outputFileId, readsetId = readsetId)
    if (nrow(toAppend) != 1) {
        warning(paste0('Error saving seurat objects, more than one row:'))
        print(toAppend)
        stop('Error saving seurat objects, more than one row!')
    }

    write.table(toAppend, file = 'savedSeuratObjects.txt', quote = FALSE, sep = '\t', row.names = FALSE, col.names = FALSE, append = TRUE)
    print(paste0('Total lines in savedSeuratObjects.txt after save:', length(readLines('savedSeuratObjects.txt'))))

    # Write cell barcodes and metadata:
    metaDf <- seuratObj@meta.data
    metaDf$cellbarcode <- colnames(seuratObj)
    write.table(metaDf, file = metaFile, quote = T, row.names = F, sep = ',', col.names = T)
    write.table(data.frame(CellBarcode = colnames(seuratObj)), file = barcodeFile, quote = F, row.names = F, sep = ',', col.names = F)
}

intermediateFiles <- c()
addIntermediateFile <- function(f) {
    print(paste0('Adding intermediate file: ', f))
    intermediateFiles <<- c(intermediateFiles, f)
}

makeLegalFileName <- function(fn) {
    fn <- gsub(fn, pattern = '\\\\', replacement = '_')
    fn <- gsub(fn, pattern = '[/ ,;]', replacement = '_')
    fn <- gsub(fn, pattern = '\\|', replacement = '_')
    return(fn)
}

errorMessages <- c()
addErrorMessage <- function(f) {
    print(paste0('Adding error: ', f))
    errorMessages <<- c(errorMessages, f)
}

print('Updating future.globals.maxSize')
options(future.globals.maxSize = Inf)

options('Seurat.memsafe' = TRUE)

if (Sys.getenv('SEURAT_MAX_THREADS') != '') {
    print(paste0('Setting future::plan workers to: ', Sys.getenv('SEURAT_MAX_THREADS')))
    future::plan(strategy='multisession', workers=Sys.getenv('SEURAT_MAX_THREADS'))
}
