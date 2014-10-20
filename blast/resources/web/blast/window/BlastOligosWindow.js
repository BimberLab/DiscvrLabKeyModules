Ext4.define('BLAST.window.BlastOligosWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();

            if (!checked.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.wait('Loading...');
            LABKEY.Query.selectRows({
                schemaName: 'laboratory',
                queryName: 'dna_oligos',
                filters: [
                    LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
                ],
                columns: 'name,sequence',
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function (data) {
                    Ext4.Msg.hide();

                    if (!data || !data.rows) {
                        Ext4.Msg.alert('Error', 'No matching oligos found');
                    }

                    var txt = '';
                    Ext4.Array.forEach(data.rows, function(row){
                        txt += '>' + row.name + '\n' + row.sequence + '\n';
                    }, this);

                    var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                        '<form method="POST" action="' + LABKEY.ActionURL.buildURL("blast", "blast") + '">' +
                        '<input type="hidden" name="querySequence" value="' + Ext4.htmlEncode(txt) + '" />' +
                        '</form>');
                    newForm.submit();
                }
            });
        }
    }
})