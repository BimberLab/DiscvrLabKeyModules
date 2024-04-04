if (!file.exists('/homeDir/.netrc')) {
    print(list.files('/homeDir'))
    stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (!'HasCDR3Data' %in% names(seuratObj@meta.data)){
        seuratObj <- seuratObj <- Rdiscvr::DownloadAndAppendTcrClonotypes(seuratObj, allowMissing = FALSE)
    }

    outFile <- paste0(outputPrefix, '.', makeLegalFileName(datasetId), '.activation.txt')
    Rdiscvr::SummarizeTNK_Activation(seuratObj, outFile = outFile, xFacetField = xFacetField, groupingFields = groupingFields, activationFieldName = activationFieldName, threshold = threshold)

    # Cleanup
    rm(seuratObj)
    gc()
}