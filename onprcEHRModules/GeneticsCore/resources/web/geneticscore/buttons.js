Ext4.ns('GeneticsCore');

GeneticsCore.buttons = new function(){
    return {
        addGeneticsFlagsForSamples: function(dataRegionName, el){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if(!checked || !checked.length){
                alert('No records selected');
                return;
            }

            var ctx = EHR.Utils.getEHRContext();
            if(!ctx){
                alert('EHRStudyContainer has not been set in this folder');
                return;
            }

            var subjects = [];
            LABKEY.Query.selectRows({
                requiredVersion: 9.1,
                schemaName: 'laboratory',
                queryName: 'samples',
                columns: 'rowid,subjectid,container',
                filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    var hasError = false;
                    Ext4.each(results.rows, function(r){
                        var row = new LDK.SelectRowsRow(r);
                        if (Ext4.isEmpty(row.getValue('subjectId'))){
                            Ext4.Msg.alert('Error', 'One or more rows does not have a value for subjectId and cannot be marked as sent for parentage');
                            hasError = true;
                            return false;
                        }

                        subjects.push(row.getValue('subjectId'));
                    }, this);

                    if (hasError)
                        return;

                    Ext4.create('GeneticsCore.window.ManageFlagsWindow', {
                        mode: 'add',
                        dataRegion: dataRegion,
                        subjectIds: subjects,
                        successHandler: function(){
                            Ext4.Msg.hide();
                            this.close();

                            Ext4.Msg.confirm('Success', 'Flags have been added.  Do you want to mark these samples as removed?', function(val){
                                if (val == 'yes'){
                                    Laboratory.buttonHandlers.markSamplesRemoved(this.dataRegion.name);
                                }
                                else {
                                    this.dataRegion.refresh();
                                }
                            }, this);
                        }
                    }).show(el);
                }
            });
        },

        manageGeneticsFlags: function(dataRegionName, el, mode){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if(!checked || !checked.length){
                alert('No records selected');
                return;
            }

            var ctx = EHR.Utils.getEHRContext();
            if(!ctx){
                alert('EHRStudyContainer has not been set in this folder');
                return;
            }

            if (!ctx.EHRStudyContainerInfo || ctx.EHRStudyContainerInfo.effectivePermissions.indexOf(EHR.Security.Permissions.DATA_ENTRY) == -1){
                alert('You do not have data entry permission in EHR');
                return;
            }

            Ext4.create('GeneticsCore.window.ManageFlagsWindow', {
                mode: mode || 'add',
                subjectIds: checked,
                dataRegion: dataRegion
            }).show(el);
        }
    }
};
