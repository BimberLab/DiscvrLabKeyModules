netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
    print(list.files(Sys.getenv('USER_HOME')))
    stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

GenerateAveragedData <- function(seuratObj, groupFields, addMetadata) {
    if (addMetadata && !'cDNA_ID' %in% names(seuratObj@meta.data)) {
        stop('A field names cDNA_ID must exist when addMetadata=TRUE')
    }

    if (addMetadata && !'cDNA_ID' %in% groupFields) {
        stop('When addMetadata=TRUE, cDNA_ID must be part of groupFields')
    }

    if (!all(is.null(additionalFieldsToAggregate))) {
        if (any(grepl(additionalFieldsToAggregate, pattern = '\\*'))) {
            additionalFieldsToAggregateExpanded <- c()
            for (fn in additionalFieldsToAggregate) {
                if (grepl(fn, pattern = '\\*')) {
                    matchingCols <- grep(names(seuratObj@meta.data), pattern = fn, value = T)
                    print(paste0('Expanding ', fn, ' into: ', paste0(matchingCols, collapse = ', ')))
                    additionalFieldsToAggregateExpanded <- c(additionalFieldsToAggregateExpanded, matchingCols)
                } else {
                    additionalFieldsToAggregateExpanded <- c(additionalFieldsToAggregateExpanded, fn)
                }

                additionalFieldsToAggregate <- unique(additionalFieldsToAggregateExpanded)
            }
        }
    }

    a <- CellMembrane::PseudobulkSeurat(seuratObj, groupFields = groupFields, assayToAggregate = assayName, additionalFieldsToAggregate = additionalFieldsToAggregate, nCountRnaStratification = nCountRnaStratification)
    if (is.null(a)) {
      return(NULL)
    }

    if (addMetadata) {
        a <- Rdiscvr::QueryAndApplyMetadataUsingCDNA(a)
    }

    return(a)
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- GenerateAveragedData(seuratObj, groupFields = groupFields, addMetadata = addMetadata)
    if (is.null(seuratObj)) {
      next
    }
    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}