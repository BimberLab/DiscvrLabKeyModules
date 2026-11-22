function afterInsert() {
    org.labkey.api.laboratory.LaboratoryService.get().clearDataProviderCache();
}

function afterUpdate() {
    org.labkey.api.laboratory.LaboratoryService.get().clearDataProviderCache();
}

function afterDelete() {
    org.labkey.api.laboratory.LaboratoryService.get().clearDataProviderCache();
}