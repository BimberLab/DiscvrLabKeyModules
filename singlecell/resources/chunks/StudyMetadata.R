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

    if (studyName == 'PC475') {
        seuratObj <- Rdiscvr::ApplyPC475Metadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'TB') {
        seuratObj <- Rdiscvr::ApplyTBMetadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'Malaria') {
        seuratObj <- Rdiscvr::ApplyMalariaMetadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'PC531') {
        seuratObj <- Rdiscvr::ApplyPC531Metadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'AcuteNx') {
        seuratObj <- Rdiscvr::ApplyAcuteNxMetadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'EC') {
        seuratObj <- Rdiscvr::ApplyEC_Metadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'PPG_Stims') {
        seuratObj <- Rdiscvr::ApplyPPG_Stim_Metadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else if (studyName == 'IMPAC_TB_Human') {
        seuratObj <- Rdiscvr::ApplyIMPAC_TB_Human_Metadata(seuratObj, errorIfUnknownIdsFound = errorIfUnknownIdsFound)
    } else {
        stop(paste0('Unknown study: ', studyName))
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}