/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var console = require("console");
var LABKEY = require("labkey");

exports.init = function(EHR){
    var Security = EHR.Server.Security;

    function isBeforeLastInvoice(date){
        var billingHelper = new org.labkey.onprc_billing.query.BillingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);
        return billingHelper.isBeforeLastInvoice(date);
    }

    function processInsert(helper, tableName, row, oldRow){
        //console.log('processing insert into: ' + tableName);
        Security.init(helper);

        //only worry about the newly inserted record if either the create date or enddate are prior to the last invoice date
        if (!isBeforeLastInvoice(row.date) && !(row.enddate && isBeforeLastInvoice(row.enddate))){
            return;
        }

        Security.normalizeQcState(row, oldRow);
        if (!EHR.Server.Security.getQCStateByLabel(row.QCStateLabel).PublicData && !(oldRow && EHR.Server.Security.getQCStateByLabel(oldRow.QCStateLabel).PublicData)){
            return;
        }

        var objectid = row.objectid || (oldRow ? oldRow.objectid : null);
        var billingHelper = new org.labkey.onprc_billing.query.BillingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);
        billingHelper.addAuditEntry(tableName, objectid, 'A record was inserted into ' + tableName + ' with a date prior to the last invoice date');
    }

    function processUpdate(helper, tableName, fields, row, oldRow){
        //console.log('processing update for: ' + tableName);

        var changed = [];
        for (var i=0;i<fields.length;i++){
            var field = fields[i];

            //this is sort of a hack, but allows date comparisons.  maybe farm out to jave helper?
            var rowVal = row[field];
            if (rowVal && rowVal.getTime){
                rowVal = rowVal.getTime();
            }

            var oldRowVal = oldRow[field];
            if (oldRowVal && oldRowVal.getTime){
                oldRowVal = oldRowVal.getTime();
            }
            if (rowVal != oldRowVal){
                changed.push(field);
            }
        }

        if (changed.length){
            Security.init(helper);
            Security.normalizeQcState(row, oldRow);

            if (!EHR.Server.Security.getQCStateByLabel(row.QCStateLabel).PublicData && !(oldRow && EHR.Server.Security.getQCStateByLabel(oldRow.QCStateLabel).PublicData)){
                return;
            }

            var shouldLog = false;
            if (changed.indexOf('date') > -1 || changed.indexOf('enddate') > -1){
                shouldLog = true;
            }

            if (!shouldLog){
                if (isBeforeLastInvoice(row.date) || (row.enddate && isBeforeLastInvoice(row.enddate))){
                    shouldLog = true;
                }
            }

            if (shouldLog){
                var objectid = row.objectid || oldRow.objectid;
                var billingHelper = new org.labkey.onprc_billing.query.BillingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);
                billingHelper.addAuditEntry(tableName, objectid, 'For the table: ' + tableName + ' a record was updated and the following fields were changed: ' + changed.join(', '));
            }
        }
    }
    
    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.AFTER_UPSERT, 'study', 'Assignment', function(helper, scriptErrors, row, oldRow){
        if (row && !oldRow){
            processInsert(helper, 'assignment', row, oldRow);
        }
        else if (row && oldRow){
            processUpdate(helper, 'assignment', ['project', 'Id', 'date', 'assignCondition', 'projectedReleaseCondition', 'releaseCondition'], row, oldRow);
        }
    });

    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.AFTER_UPSERT, 'study', 'Housing', function(helper, scriptErrors, row, oldRow){
        if (row && !oldRow){
            processInsert(helper, 'housing', row, oldRow);
        }
        else if (row && oldRow){
            processUpdate(helper, 'housing', ['Id', 'date', 'enddate', 'room', 'cage'], row, oldRow);
        }
    });

    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.AFTER_UPSERT, 'study', 'Clinical Encounters', function(helper, scriptErrors, row, oldRow){
        if (row && oldRow){
            processUpdate(helper, 'encounters', ['project', 'Id', 'date', 'chargetype', 'procedureid'], row, oldRow);
        }
    });

    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.AFTER_UPSERT, 'study', 'Clinpath Runs', function(helper, scriptErrors, row, oldRow){
        if (row && oldRow){
            processUpdate(helper, 'clinpathRuns', ['project', 'Id', 'date', 'chargetype', 'servicerequested'], row, oldRow);
        }
    });

    EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.AFTER_UPSERT, 'study', 'Blood Draws', function(helper, scriptErrors, row, oldRow){
        console.log('inspecting blood draws record for billing audit');

        if (row && oldRow){
            processUpdate(helper, 'blood', ['project', 'Id', 'date', 'chargetype', 'reason'], row, oldRow);
        }
    });
};