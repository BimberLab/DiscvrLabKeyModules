if (!file.exists('/homeDir/.netrc')) {
    print(list.files('/homeDir'))
    stop('Unable to find file: /homeDir/.netrc')
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = '/homeDir/.netrc'))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- Rdiscvr::RunCoNGA(seuratObj, organism = organism, assayName = assayName, congaMetadataPrefix = paste0(congaMetadataPrefix, '.'), runCongaOutputFilePrefix = 'conga_output', pngConversionTool = pngConversionTool)

    if (!is.null(fieldToIterate)) {
        print(paste0('Will iterate all values of field: ', fieldToIterate))
        if (!fieldToIterate %in% names(seuratObj@meta.data)) {
            stop(paste0('Missing field: ', fieldToIterate))
        }

        if (!(is.factor(seuratObj@meta.data[[fieldToIterate]]) || is.character(seuratObj@meta.data[[fieldToIterate]]))) {
            stop(paste0('Field to iterate must be a character or factor: ', fieldToIterate))
        }

        values <- sort(unique(seuratObj@meta.data[[fieldToIterate]]))
        for (value in values) {
            cells <- colnames(seuratObj)[seuratObj@meta.data[[fieldToIterate]] == value]
            ss <- subset(seuratObj, cells = cells)
            print(paste0('Processing subset: ', value, ' with ', ncol(ss), ' cells'))
            prefix <- paste0(congaMetadataPrefix, '.', value, '.')
            ss <- Rdiscvr::RunCoNGA(ss,
                                    organism = organism,
                                    assayName = assayName,
                                    congaMetadataPrefix = prefix,
                                    runCongaOutputDirectory = paste0('conga_output_', value),
                                    runCongaOutputFilePrefix = 'conga_output'
            )

            fieldsToAppend <- grep(x = names(ss@meta.data), pattern = paste0('^', prefix), value = TRUE)
            toAppend <- ss@meta.data[fieldsToAppend]
            seuratObj <- Seurat::AddMetaData(seuratObj, toAppend)
            rm(ss)
        }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}