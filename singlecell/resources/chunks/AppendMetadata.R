netRc <- paste0(Sys.getEnv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
    print(list.files(Sys.getEnv('USER_HOME')))
    stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- Rdiscvr::QueryAndApplyCdnaMetadata(seuratObj)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}