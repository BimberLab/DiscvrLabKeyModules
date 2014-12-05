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
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            modal: true,
            title: 'Download Sequences',
            width: 500,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                labelWidth: 180
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
            },{
                xtype: 'checkbox',
                itemId: 'customIntervals',
                fieldLabel: 'Download Specific Regions',
                helpPopup: 'If checked, you will be asked to provide specific sub-regions of the reference to download, as opposed to the entire sequence',
                listeners: {
                    scope: this,
                    change: function(field, val){
                        var target = field.up('panel').down('#intervalRegion');
                        target.removeAll();
                        if (val){
                            Ext4.Msg.wait('Loading...');

                            LABKEY.Query.selectRows({
                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                schemaName: 'sequenceanalysis',
                                queryName: 'ref_nt_sequences',
                                columns: 'rowid,name',
                                filterArray: [LABKEY.Filter.create('rowid', this.rowIds.join(';'), LABKEY.Filter.Types.IN)],
                                scope: this,
                                failure: LDK.Utils.getErrorCallback(),
                                success: function(results) {
                                    Ext4.Msg.hide();

                                    if (!results || !results.rows || !results.rows.length) {
                                        Ext4.Msg.alert('Error', 'Error, no matching rows found');
                                        return;
                                    }

                                    var toAdd = [{
                                        html: 'Enter desired intervals below in the format start-stop (ie. 300-400).  To specify multiple intervals, separate with commas (ie. 300-400,500-600).',
                                        style: 'padding-bottom: 10px;',
                                        border: false
                                    }];

                                    Ext4.Array.forEach(results.rows, function (row) {
                                        toAdd.push({
                                            xtype: 'textfield',
                                            name: 'interval',
                                            width: 350,
                                            labelWidth: 180,
                                            refId: row.rowid,
                                            fieldLabel: row.name,
                                            validator: function(val){
                                                if (val === null || val === ''){
                                                    return true;
                                                }

                                                if (val.match(/ /g)){
                                                    return 'The value cannot contain spaces';
                                                }

                                                var tokens = val.split(',');
                                                var ret = true;
                                                Ext4.Array.forEach(tokens, function(t){
                                                    if (!t.match(/^[0-9]+-[0-9]+$/)){
                                                        ret =  'Invalid interval: [' + t + ']';
                                                        return false;
                                                    }
                                                }, this);

                                                return ret;
                                            }
                                        });
                                    }, this);

                                    target.add(toAdd);
                                }
                            });
                        }
                    }
                }
            },{
                itemId: 'intervalRegion',
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

        var intervalMap = {};
        if (this.down('#customIntervals').getValue()){
            var error = false;
            Ext4.Array.forEach(this.query('field[name=interval]'), function(field){
                if (!field.isValid()){
                    error = true;
                    return false;
                }

                intervalMap[field.refId] = field.getValue();
            }, this);

            if (error){
                Ext4.Msg.alert('Error', 'One or more intervals in not valid.  Hover over the red fields for more detail.');
                return;
            }
        }

        var params = {rowIds: this.rowIds, headerFormat: formatString, fileName: fileName, lineLength: lineLength};
        if (!Ext4.isEmpty(intervalMap)){
            params.intervals = Ext4.encode(intervalMap);
        }

        var form = Ext4.create('Ext.form.Panel', {
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'downloadReferences', null, params),
            standardSubmit: true
        });
        form.submit();
        this.close();
    }
});