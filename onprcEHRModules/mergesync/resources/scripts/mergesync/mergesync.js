/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

exports.init = function(EHR){
    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.COMPLETE, 'study', 'Clinpath Runs', function(event, errors, helper){
        var rows = helper.getRows();
        if (helper.getEvent() == 'insert' && rows.length){
            var toSync = [];
            for (var i=0;i<rows.length;i++){
                var row = rows[i].row;
                var qc;
                if (row.QCStateLabel){
                    qc = EHR.Server.Security.getQCStateByLabel(row.QCStateLabel);
                }
                else if (row.QCState){
                    qc = EHR.Server.Security.getQCStateByRowId(row.QCState);
                }

                if (!qc){
                    console.log('Unable to find QCState: ' + row.QCState);
                }
                else if (qc.isRequest){
                    toSync.push(row)
                }
            }

            if (toSync.length){
                var mergeHelper = new org.labkey.mergesync.RequestSyncHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);
                console.log('syncing ' + toSync.length + ' rows to merge');
                mergeHelper.asyncRequests(toSync);
            }
        }
    });
}