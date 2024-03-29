if (!file.exists('/homeDir/.netrc')) {
    print(list.files('/homeDir'))
    stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = allowMissing)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}