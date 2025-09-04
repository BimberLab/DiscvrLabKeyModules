for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

  Rdiscvr::IdentifyAndStoreActiveClonotypes(seuratObj, chain = 'TRA')
  Rdiscvr::IdentifyAndStoreActiveClonotypes(seuratObj, chain = 'TRB')

  saveData(seuratObj, datasetId)

  # Cleanup
  rm(seuratObj)
  gc()
}