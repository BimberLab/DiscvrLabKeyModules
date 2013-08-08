Ext4.define('SequenceAnalysis.window.IlluminaSampleKitWindow', {
    extend: 'Ext.window.Window',
    modal: true,
    closeAction: 'destroy',
    title: 'Import Illumina Sample Kit',

    items: [{
        xtype: 'form',
        width: 612,
        labelPosition: 'top',
        bodyStyle: 'padding: 5px;',
        defaults: {
            border: false
        },
        items: [{
            html: 'This window allows the import of an existing Illumina sample kit from a text file.<br><br>Simply cut/paste the contents of the Illumina application file into the box below and hit save.  This file is a .txt file that is used internally by Illumina Experiment Manager.  The files can be found in the /SamplePrepKits subfolder in the directory where IEM is installed.',
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
                Ext4.Msg.alert('Must paste the contents of an Illumina sample kit');
                return;
            }

            var params = win.parseText(text);
            win.saveKit(params);
        }
    },{
        text: 'Cancel',
        handler: function(btn){
            var win = btn.up('window');
            win.close();
        }
    }],

    saveKit: function(params){
        var name = params['Name'];
        if (!name){
            Ext4.Msg.alert('Error', 'Name was not provided');
            return;
        }
        name = name[0][0];

        var json = {
            Settings: params.Settings
        };

        LABKEY.Query.selectRows({
            schemaName: 'sequenceanalysis',
            queryName: 'illumina_sample_kits',
            columns: '*',
            filterArray: [LABKEY.Filter.create('name', name, LABKEY.Filter.Types.EQUAL)],
            scope: this,
            failure: LDK.Utils.getErrorCallback({showAlertOnError: true}),
            success: function(response){
                var row = {
                    name: name,
                    json: LABKEY.ExtAdapter.encode(json)
                };

                var config = {
                    schemaName: 'sequenceanalysis',
                    queryName: 'illumina_sample_kits',
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
        });
    },

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

            var cells = line.split('\t');
            vals[activeSection].push(cells);
        }, this);

        var ret = {};
        for (var i in vals){
            if (vals[i] === null){
                console.log('skipping: ' + i);
                continue;
            }

            ret[i] = vals[i];
        }

        return ret;
    }
});
