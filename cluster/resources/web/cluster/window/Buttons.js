Ext4.namespace('Cluster.Utils');

Cluster.Utils = new function() {
    return {
        buttonHandlerForLog: function (dataRegionName) {
            const checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }
            else if (checked.length > 1){
                Ext4.Msg.alert('Error', 'Can only select one row at a time');
                return;
            }

            window.open(LABKEY.ActionURL.buildURL('cluster', 'viewJavaLog', null, {jobId: checked[0]}), '_blank');
        },

        recoverCompletedJobs: function (dataRegionName) {
            const checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('cluster', 'recoverCompletedJobs', null, {jobIds: checked.join(',')});
        },

        forcePipelineCancel: function (dataRegionName) {
            const checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('cluster', 'forcePipelineCancel', null, {jobIds: checked.join(',')});
        }
    };
};