netRc <- paste0(Sys.getenv('USER_HOME'), '/.netrc')
if (!file.exists(netRc)) {
    print(list.files(Sys.getenv('USER_HOME')))
    stop(paste0('Unable to find file: ', netRc))
}

invisible(Rlabkey::labkey.setCurlOptions(NETRC_FILE = netRc))
Rdiscvr::SetLabKeyDefaults(baseUrl = serverBaseUrl, defaultFolder = defaultLabKeyFolder)

if (!reticulate::py_module_available(module = 'conga')) {
    logger::log_warn('python conga not found!')
    logger::log_warn(paste0('Python available: ', reticulate::py_available()))
    logger::log_warn('Python config')
    pyConfig <- reticulate::py_config()
    for (pn in names(pyConfig)) {
        logger::log_warn(paste0(pn, ': ', paste0(pyConfig[[pn]]), collapse = ','))
    }

    logger::log_warn(paste0('pythonpath: ', reticulate::py_config()$pythonpath))

    logger::log_warn('Python packages:')
    for (pn in reticulate::py_list_packages()$package) {
        logger::log_warn(pn)
    }

    if ('conga' %in% reticulate::py_list_packages()$package) {
        tryCatch({
            logger::log_warn(reticulate::import('conga'))
        }, error = function(e){
            logger::log_warn("Error with reticulate::import('conga')")
            logger::log_warn(reticulate::py_last_error())
            logger::log_warn(conditionMessage(e))
            traceback()
        })
    }
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    message('Processing entire dataset')
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
            message(paste('Processing: ', value))
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