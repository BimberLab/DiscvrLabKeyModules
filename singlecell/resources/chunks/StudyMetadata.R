if (!file.exists('/homeDir/.netrc')) {
    print(list.files('/homeDir'))
    stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

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
    } else {
        stop(paste0('Unknown study: ', studyName))
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}