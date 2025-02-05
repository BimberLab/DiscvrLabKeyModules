if (!reticulate::py_module_available(module = 'celltypist')) {
    logger::log_warn('python celltypist not found!')
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
            logger::log_warn(reticulate::import('celltypist'))
        }, error = function(e){
            logger::log_warn("Error with reticulate::import('celltypist')")
            logger::log_warn(reticulate::py_last_error())
            logger::log_warn(conditionMessage(e))
            traceback()
        })
    }
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (mn in modelNames) {
        seuratObj <- RIRA::RunCellTypist(seuratObj, modelName = paste0(mn, '.pkl'), columnPrefix = paste0('celltypist.', mn, '.'), pThreshold = pThreshold, minProp = minProp, maxAllowableClasses = maxAllowableClasses, minFractionToInclude = minFractionToInclude, useMajorityVoting = useMajorityVoting, mode = mode, maxBatchSize = maxBatchSize, retainProbabilityMatrix = retainProbabilityMatrix)
    }

    saveData(seuratObj, datasetId)
}