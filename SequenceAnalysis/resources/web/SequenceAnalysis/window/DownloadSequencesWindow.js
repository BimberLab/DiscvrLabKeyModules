Ext4.define('SequenceAnalysis.window.DownloadSequencesWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.create('SequenceAnalysis.window.DownloadSequencesWindow', {
                rowIds: checked
            }).show();
        },

        downloadSingle: function(rowId){
            if (!rowId){
                alert('No Row Id provided');
                return;
            }

            Ext4.create('SequenceAnalysis.window.DownloadSequencesWindow', {
                rowIds: [rowId]
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Download Sequences',
            width: 500,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Filename',
                allowBlank: false,
                width: 350,
                itemId: 'fileName',
                value: 'output.fasta'
            },{
                xtype: 'ldk-integerfield',
                fieldLabel: 'Line Length',
                itemId: 'lineLength',
                width: 350,
                value: 60,
                allowBlank: false
            },{
                xtype: 'radiogroup',
                fieldLabel: 'Header Format',
                columns: 1,
                defaults: {
                    name: 'format',
                    xtype: 'radio'
                },
                items: [{
                    boxLabel: 'Name Only',
                    inputValue: '${name}',
                    checked: true
                },{
                    boxLabel: 'Name And Id',
                    inputValue: '${rowid}|${name}'
                },{
                    boxLabel: 'Advanced',
                    inputValue: 'advanced'
                }],
                listeners: {
                    change: function (field, val) {
                        var target = field.up('window').down('#fieldExpression');
                        target.removeAll();
                        if (val.format == 'advanced') {
                            target.add([{
                                html: 'This option allows you to enter an advanced expression to create the FASTA header line.  These expressions use the same syntax as <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=urlEncoding" target="_blank">LabKey\'s column URL expressions</a>.  These allow you to substitute the value of any field into the header by enclosing it with ${}, such as: "${rowid}_${name}_${species}".',
                                style: 'padding-top: 10px;padding-bottom: 10px;',
                                border: false
                            },{
                                xtype: 'textfield',
                                fieldLabel: 'Expression',
                                allowBlank: false,
                                width: 550,
                                itemId: 'customFormat'
                            }]);
                        }
                        else {
                            //nothing needed
                        }
                    }
                }
            },{
                itemId: 'fieldExpression',
                border: false
            }],
            buttons: [{
                text: 'Download',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        var fileName = this.down('#fileName').getValue();
        if (!fileName) {
            Ext4.Msg.alert('Error', 'Must enter a filename');
            return;
        }

        var lineLength = this.down('#lineLength').getValue();
        if (!lineLength) {
            Ext4.Msg.alert('Error', 'Must enter the line length');
            return;
        }

        var formatString;
        var formatField = this.down('#customFormat');
        if (formatField){
            if (!formatField.getValue()) {
                Ext4.Msg.alert('Error', 'Must enter the header expression');
                return;
            }
            else
            {
                formatString = formatField.getValue();
            }
        }
        else {
            formatString = this.down('radiogroup').getValue().format;
        }

        var form = Ext4.create('Ext.form.Panel', {
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'downloadReferences', null, {rowIds: this.rowIds, headerFormat: formatString, fileName: fileName, lineLength: lineLength}),
            standardSubmit: true
        });
        form.submit();
        this.close();
    }
});