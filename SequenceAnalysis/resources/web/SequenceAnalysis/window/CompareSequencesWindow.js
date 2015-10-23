Ext4.define('SequenceAnalysis.window.CompareSequencesWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('SequenceAnalysis.window.CompareSequencesWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            modal: true,
            title: 'Compare FASTA Against Existing Sequences',
            width: 800,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'This helper allows you to provide a FASTA file with sequences, which will be compared against existing sequences in the database.  For each sequence in the FASTA, we will report whether there is an existing sequence of the same name, and if there is an exact match we will compare the sequences to determine if they are exact or if one is a subset of the other.  We will also check for existing references(s) with sequences that are identical or a subset of the incoming sequence.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textarea',
                fieldLabel: 'FASTA',
                itemId: 'fasta',
                height: 300,
                width: 750
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Category (for comparisons)',
                width: 400,
                itemId: 'category',
                displayField: 'category',
                valueField: 'category',
                store: {
                    type: 'labkey-store',
                    schemaName: 'sequenceanalysis',
                    sql: 'SELECT DISTINCT category from sequenceanalysis.ref_nt_sequences',
                    autoLoad: true
                }
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Species (for comparisons)',
                width: 400,
                itemId: 'species',
                displayField: 'species',
                valueField: 'species',
                store: {
                    type: 'labkey-store',
                    schemaName: 'sequenceanalysis',
                    sql: 'SELECT DISTINCT species from sequenceanalysis.ref_nt_sequences',
                    autoLoad: true
                }
            },{
                xtype: 'checkbox',
                fieldLabel: 'Include Disabled Sequence?',
                width: 400,
                itemId: 'includeDisabled',
                checked: false
            }],
            buttons: [{
                text: 'Submit',
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

    onSubmit: function(){
        var fasta = this.down('#fasta').getValue();
        var category = this.down('#category').getValue();
        var species = this.down('#species').getValue();
        var includeDisabled = this.down('#includeDisabled').getValue();

        if (!fasta){
            Ext4.Msg.alert('Error', 'Must provide the FASTA sequence');
            return;
        }

        Ext4.Msg.wait('Loading...');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'compareFastaSequences'),
            jsonData: {
                fasta: fasta,
                category: category,
                species: species,
                includeDisabled: includeDisabled
            },
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: LABKEY.Utils.getCallbackWrapper(this.onDataLoaded, this, false)
        });
    },

    onDataLoaded: function(results){
        Ext4.Msg.hide();

        var text = ['Name\tNumHits\tRefName\tRefId\tSequencesMatch\tFastaSequenceIsSubsetOfReference\tReferenceSequenceIsSubsetOfFasta\tFastaLength\tRefLength'];
        if (results.hits){

            Ext4.Array.forEach(results.hits, function(h){
                if (h.hits.length) {
                    Ext4.Array.forEach(h.hits, function (hit) {
                        text.push([h.name, h.hits.length, hit.refName, hit.refId, hit.sequencesMatch, hit.fastaSequenceIsSubsetOfReference, hit.referenceSequenceIsSubsetOfFasta, hit.fastaLength, hit.refLength].join('\t'))
                    }, this);
                }
                else
                {
                    text.push([h.name, 'No Hits'].join('\t'));
                }
            }, this);
        }
        this.close();
        Ext4.create('Ext.window.Window', {
            title: 'Results',
            modal: true,
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'textarea',
                width: 800,
                height: 400,
                value: text.join('\n')
            }],
            buttons: [{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        }).show();
    }
});