netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
    print(list.files(Sys.getenv('USER_HOME')))
    stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc, timeout = 60, timeout_ms = 60000, connecttimeout = 20, connecttimeout_ms = 20000))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

curlOpt <- curl::curl_options('timeout')
logger::log_info('Curl options:')
for (x in names(curlOpt)) {
    logger::log_info(paste0(x, ': ', curlOpt[x]))
}
rm(curlOpt)

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