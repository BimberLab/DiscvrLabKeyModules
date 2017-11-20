var console = require("console");

function init(event, errors) {
    return;
    console.log('init called');
}

function beforeInsert(row, errors){


    return;
    // If we don't have the AnimalId for row, get it from the SampleInventory table
    if (row.AnimalId === undefined || row.AnimalId == 'undefined')
        {
            console.log('beforeInsert called');
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'SampleInventory',
                columns: 'AnimalId',
                scope: this,
                filterArray: [
                    LABKEY.Filter.create('SampleId', row.SampleId, LABKEY.Filter.Types.EQUAL)
                ],
                success: function (data)
                {
                    if (data.rows && data.rows.length)
                    {
                        row.AnimalId = data.rows[0].AnimalId;
                        console.log('Found Animal Id for  ' + row.SampleId + ': ' + row.AnimalId);
                    }
                },
                failure: function (error)
                {
                    console.log('Select rows error');
                    console.log(error);
                }
            });
        }
    }
