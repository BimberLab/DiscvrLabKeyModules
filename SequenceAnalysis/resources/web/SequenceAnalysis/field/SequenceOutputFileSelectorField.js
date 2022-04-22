Ext4.define('SequenceAnalysis.field.SequenceOutputFileSelectorField', {
    extend: 'LABKEY.ext4.ComboBox',
    alias: 'widget.sequenceanalysis-sequenceoutputfileselectorfield',

    genomeId: -1,

    initComponent: function(){
        Ext4.apply(this, {
            forceSelection: true,
            displayField: 'name',
            valueField: 'dataid',
            listConfig: {
                innerTpl: ['{name} ({[values["rowid"]]})']
            },
            store: {
                type: 'labkey-store',
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'outputfiles',
                autoLoad: true,
                filterArray: this.getFilterArray(),
                sort: 'name',
                columns: 'library_id,name,dataid,category'
            }
        });

        this.callParent(arguments);

        this.on('afterrender', function() {
            var parent = this.up('sequenceanalysis-basesequencepanel');  //Alignment panels
            var window = this.up('window'); //OutputHandlerWindow
            if (parent) {
                var field = parent.down('field[name=referenceLibraryCreation.SavedLibrary.libraryId]');
                if (field) {
                    this.mon(field, 'change', this.onGenomeChange, this);
                    if (field.getValue()){
                        this.updateStoreFilters(field.getValue());
                    }
                }
                else if (parent.libraryIds){
                    if (parent.libraryIds.length === 1){
                        this.updateStoreFilters(parent.libraryIds[0]);
                    }
                    else if (!parent.libraryIds.length){
                        Ext4.Msg.alert('Genome Error', 'There are no reference genomes associated with these samples');
                    }
                    else if (parent.libraryIds.length > 1){
                        Ext4.Msg.alert('Genome Error', 'More than one reference genome associated with these samples.  They must all use the same genome.');
                    }
                }
                else {
                    LDK.Utils.logError('unable to find library field in SequenceOutputFileSelectorField');
                }
            }
            else if (window && window.libraryId){
                this.updateStoreFilters(window.libraryId);
            }
            else {
                LDK.Utils.logError('unable to find basesequencepanel in SequenceOutputFileSelectorField');
                Ext4.Msg.alert('Error', 'There is no genome ID provided to this field.  This indicates an error in how the module was developed - please contact your administrator.');
            }
        }, this);
    },

    onGenomeChange: function(field, genomeId){
        genomeId = genomeId ? genomeId : -1;
        if (genomeId === this.genomeId){
            return;
        }

        this.updateStoreFilters(genomeId);
    },

    updateStoreFilters: function(genomeId){
        this.genomeId = genomeId;
        this.store.filterArray = this.getFilterArray();
        this.store.load();
    },

    getFilterArray: function(){
        return [LABKEY.Filter.create('library_id', this.genomeId), LABKEY.Filter.create('category', this.category, LABKEY.Filter.Types.EQUAL)];
    }
});