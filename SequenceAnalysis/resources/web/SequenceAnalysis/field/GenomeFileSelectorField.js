Ext4.define('SequenceAnalysis.field.GenomeFileSelectorField', {
    extend: 'LABKEY.ext4.ComboBox',
    alias: 'widget.sequenceanalysis-genomefileselectorfield',

    genomeId: -1,

    initComponent: function(){
        Ext4.apply(this, {
            forceSelection: true,
            displayField: 'fileid/Name',
            valueField: 'fileid',
            listConfig: {
                innerTpl: ['{name} ({[values["fileid/Name"]]})']
            },
            store: {
                type: 'labkey-store',
                containerPath: Laboratory.Utils.getQueryContainerPath(),
                schemaName: 'sequenceanalysis',
                queryName: 'reference_library_tracks',
                autoLoad: true,
                filterArray: this.getTracksFilterArray(this.genomeId),
                sort: 'fileid/name',
                columns: 'library_id,name,fileid,fileid/Name,fileid/FileExtension',
                listeners: {
                    scope: this,
                    load: function(store){
                        if (this.extensions) {
                            Ext4.Array.forEach(store.getRange(), function (r) {
                                if (this.extensions.indexOf(r.get('fileid/FileExtension')) === -1){
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
                    LDK.Utils.logError('unable to find library field in GenomeFileSelectorField');
                }
            }
            else if (window && window.libraryId){
                this.updateStoreFilters(window.libraryId);
            }
            else {
                LDK.Utils.logError('unable to find basesequencepanel in GenomeFileSelectorField');
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
        this.store.filterArray = this.getTracksFilterArray(genomeId);
        this.store.load();
    },

    getTracksFilterArray: function(genomeId){
        return [LABKEY.Filter.create('library_id', this.genomeId), LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)];
    }
});