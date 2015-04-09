Ext4.define('SequenceAnalysis.window.GenomeFileSelectorField', {
    extend: 'LABKEY.ext4.ComboBox',
    alias: 'widget.sequenceanalysis-genomefileselectorfield',

    genomeId: -1,

    initComponent: function(){
        Ext4.apply(this, {
            forceSelection: true,
            displayField: 'fileid/Name',
            valueField: 'fileid',
            store: {
                type: 'labkey-store',
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'reference_library_tracks',
                autoLoad: true,
                filterArray: [LABKEY.Filter.create('library_id', this.genomeId)],
                sort: 'fileid/name',
                columns: 'library_id,fileid,fileid/Name,fileid/FileExtension',
                listeners: {
                    scope: this,
                    load: function(store){
                        if (this.extensions) {
                            Ext4.Array.forEach(store.getRange(), function (r) {
                                if (this.extensions.indexOf(r.get('fileid/FileExtension')) == -1){
                                    store.remove(r);
                                }
                            }, this);
                        }
                    }
                }
            }
        });

        this.callParent(arguments);

        this.on('afterrender', function() {
            var parent = this.up('sequenceanalysis-basesequencepanel');
            if (parent) {
                var field = parent.down('field[name=referenceLibraryCreation.SavedLibrary.libraryId]');
                if (field) {
                    this.mon(field, 'change', this.onGenomeChange, this);
                    if (field.getValue()){
                        this.updateStoreFilters(field.getValue());
                    }
                }
                else {
                    LDK.Utils.logError('unable to find library field in GenomeFileSelectorField');
                }
            }
            else {
                LDK.Utils.logError('unable to find basesequencepanel in GenomeFileSelectorField');
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
        this.store.filterArray = [LABKEY.Filter.create('library_id', this.genomeId)];
        this.store.load();
    }
});