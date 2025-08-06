netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
    print(list.files(Sys.getenv('USER_HOME')))
    stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc, timeout = 60, timeout_ms = 60000))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if ('BarcodePrefix' %in% names(seuratObj@meta.data) && !any(is.na(as.integer(seuratObj$BarcodePrefix)))) {
        seuratObj <- Rdiscvr::QueryAndApplyCdnaMetadata(seuratObj)
    } else if ('cDNA_ID' %in% names(seuratObj@meta.data)) {
        seuratObj <- Rdiscvr::QueryAndApplyMetadataUsingCDNA(seuratObj)
    } else {
        stop('Unable to find either BarcodePrefix or cDNA_ID in meta.data')
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}