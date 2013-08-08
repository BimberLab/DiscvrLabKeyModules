Ext4.define('SequenceAnalysis.window.IlluminaApplicationWindow', {
    extend: 'Ext.window.Window',
    modal: true,
    closeAction: 'destroy',
    title: 'Import Illumina Application',

    items: [{
        xtype: 'form',
        width: 612,
        labelPosition: 'top',
        bodyStyle: 'padding: 5px;',
        defaults: {
            border: false
        },
        items: [{
            html: 'This window allows the import of an existing Illumina application from a text file.<br><br>Simply cut/paste the contents of the Illumina application file into the box below and hit save.  This file is a .txt file that is used internally by Illumina Experiment Manager.  The files can be found in the /Applications subfolder in the directory where IEM is installed.',
            bodyStyle: 'padding-bottom: 15px;'
        },{
            xtype: 'textarea',
            labelPosition: 'top',
            itemId: 'textArea',
            width: 600,
            height: 300,
            allowBlank: false
        }]
    }],

    buttons: [{
        text: 'Save',
        handler: function(btn){
            var win = btn.up('window');
            var text = win.down('textarea').getValue();
            if (!text){
                Ext4.Msg.alert('Must paste the contents of an Illumina application');
                return;
            }

            var params = win.parseText(text);
            win.saveApplication(params);
        }
    },{
        text: 'Cancel',
        handler: function(btn){
            var win = btn.up('window');
            win.close();
        }
    }],

    parseText: function(text){
        text = text.split(/[\r\n|\r|\n]+/g);

        var vals = {};
        var activeSection = '';
        var errors = [];
        Ext4.each(text, function(line, idx){
            if(!line)
                return;

            if(line.match(/^\[/)){
                line = line.replace(/\]|\[/g, '');
                activeSection = line;
                return;
            }

            if(!vals[activeSection])
                vals[activeSection] = [];

            vals[activeSection].push(line);
        }, this);

        var ret = {};
        for (var i in vals){
            if (vals[i] === null){
                console.log('skipping: ' + i);
                continue;
            }

            if (vals[i].length == 1)
                ret[i] = vals[i][0];
            else
                ret[i] = vals[i];
        }

        for (var i in ret){
            if (i == 'Workflow-Specific Parameters'){
                var header = ret[i].shift().split('\t');
                var newRows = [];
                Ext4.each(ret[i], function(row){
                    var newRow = {};
                    row = row.split('\t');
                    Ext4.each(header, function(cell, idx){
                        newRow[cell] = row[idx];
                    });
                    newRows.push(newRow);
                });

                ret[i] = newRows;
            }
            else if (i == 'Settings'){
                var obj = {};

                Ext4.each(ret[i], function(item){
                    obj[item] = null;
                }, this);
                ret[i] = obj;
            }
        }

        return ret;
    },

    saveApplication: function(params){
        var name = params['Display Name'];
        if (!name){
            Ext4.Msg.alert('Error', 'Display Name was not provided');
            return;
        }

        LABKEY.Query.selectRows({
            schemaName: 'sequenceanalysis',
            queryName: 'illumina_applications',
            columns: '*',
            filterArray: [LABKEY.Filter.create('name', name, LABKEY.Filter.Types.EQUAL)],
            scope: this,
            failure: LDK.Utils.getErrorCallback({showAlertOnError: true}),
            success: function(response){
                var row = {
                    name: name,
                    label: params['Display Name'] || name,
                    compatiblekits: LABKEY.ExtAdapter.isArray(params['Compatible Sample Prep Kits']) ? params['Compatible Sample Prep Kits'].join(',') : params['Compatible Sample Prep Kits'],
                    workflowname: params['Workflow Name'],
                    version: params['Version'],
                    settings: LABKEY.ExtAdapter.encode(params['Settings']),
                    workflowparams: LABKEY.ExtAdapter.encode(params['Workflow-Specific Parameters'])
                };

                var config = {
                    schemaName: 'sequenceanalysis',
                    queryName: 'illumina_applications',
                    rows: [row],
                    scope: this,
                    failure: LDK.Utils.getErrorCallback({showAlertOnError: true}),
                    success: function(response){
                        Ext4.Msg.alert('Success', 'Success!', function(){
                            this.close();

                            if (this.dataRegionName)
                                LABKEY.DataRegions[this.dataRegionName].refresh();
                        }, this);
                    }
                }

                if (response.rows.length){
                    LABKEY.Query.updateRows(config);
                }
                else {
                    LABKEY.Query.insertRows(config);
                }
            }
        })
    }
});
