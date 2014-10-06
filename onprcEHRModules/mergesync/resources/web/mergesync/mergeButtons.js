Ext4.ns('MergeSync.buttons');

MergeSync.buttons = new function(){
    return {
        resyncSelected: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.Msg.wait('Loading...');
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('mergesync', 'resyncRuns', null, {pks: checked}),
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function(){
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Success', 'Runs have been resynced');
                }
            });
        }
    }
};