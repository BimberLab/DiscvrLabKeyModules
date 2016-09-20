Ext4.ns('GeneticsCore');

GeneticsCore.buttons = new function(){
    return {
        editGeneticsFlagsForSamples: function(dataRegionName, el, mode){
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

            Ext4.Msg.wait('Loading...');
            var subjects = [];
            LABKEY.Query.selectRows({
                method: 'POST',
                requiredVersion: 9.1,
                containerPath: dataRegion.containerPath,
                schemaName: 'laboratory',
                queryName: 'samples',
                columns: 'rowid,subjectid,container',
                filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.IN)],
                failure: LDK.Utils.getErrorCallback(),
                success: function(results){
                    Ext4.Msg.hide();

                    if (!results.rows || !results.rows.length){
                        Ext4.Msg.alert('Error', 'No rows found');
                        return;
                    }

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

                    if (!subjects.length){
                        Ext4.Msg.alert('Error', 'No subject IDs provided');
                        return;
                    }

                    Ext4.create('GeneticsCore.window.ManageFlagsWindow', {
                        mode: (mode || 'add'),
                        dataRegion: dataRegion,
                        subjectIds: subjects,
                        successHandler: function(response){
                            Ext4.Msg.hide();
                            this.close();

                            var added = response.added || [];
                            var removed = response.removed || [];
                            var msg = 'Flags have been updated.  A total of ' + added.length + ' animals had flags added and ' + removed.length + ' had flags removed.  These numbers may differ from the total rows selected because flags are only added/removed if the animal needs them, and will only be added to animals actively at the center.<br>Do you want to mark these samples as removed?';
                            Ext4.Msg.confirm('Success', msg, function(val){
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
        },

        sbtReviewHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('geneticscore', 'sbtReview', null, {analysisIds: checked.join(';')});
        },

        haplotypeHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                    '<form method="POST" action="' + LABKEY.ActionURL.buildURL("geneticscore", "bulkHaplotype", null) + '">' +
                    '<input type="hidden" name="analysisIds" value="' + Ext4.htmlEncode(checked.join(';')) + '" />' +
                    '</form>');
            newForm.submit();
        }
    }
};
