Ext4.define('SequenceAnalysis.window.GenomeLoadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (){
            Ext4.create('SequenceAnalysis.window.GenomeLoadWindow', {

            }).show();
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            modal: true,
            title: 'Load Genome From NCBI',
            width: 500,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                labelWidth: 180
            },
            items: [{
                html: 'This will load a reference genome from NCBI\'s FTP site, which is <a style="font-weight: bold;" href="ftp://ftp.ncbi.nlm.nih.gov/genomes/README.txt" target="_blank">described here</a>.<br><br>' +
                        'You need to supply the name of the genome directory to load.  To view available directories, <a style="font-weight: bold;" href="ftp://ftp.ncbi.nlm.nih.gov/genomes/" target="_blank">click here</a>.<br><br>' +
                        'This import is highly experimental and only tested on human.  It expects the supplied genome to have one subdirectory per chromosome, starting with \'CHR_\'.  Within each chromosome directory, there must be a file ' +
                        'with the extension \'.fa.gz\'.  If the organism you provided has more than 1 genome in the folder (which is the case for human), you must also provide the prefix for the FASTA file names.   ',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'NCBI Subfolder',
                itemId: 'folder',
                allowBlank: false,
                width: 400
            },{
                xtype: 'textfield',
                fieldLabel: 'Genome Prefix (optional)',
                itemId: 'genomePrefix',
                allowBlank: true,
                width: 400
            },{
                xtype: 'textfield',
                fieldLabel: 'Genome Name',
                itemId: 'genomeName',
                allowBlank: false,
                width: 400
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Species',
                itemId: 'species',
                allowBlank: true,
                width: 400,
                displayField: 'common_name',
                valueField: 'common_name',
                store: {
                    type: 'labkey-store',
                    autoLoad: true,
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'laboratory',
                    queryName: 'species',
                    columns: 'common_name'
                }
            }],
            buttons: [{
                text: 'Start Data Load',
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
        var folder = this.down('#folder').getValue();
        if (!folder) {
            Ext4.Msg.alert('Error', 'Must enter the name of the folder containing the genome');
            return;
        }

        var genomePrefix = this.down('#genomePrefix').getValue();
        var species = this.down('#species').getValue();

        var genomeName = this.down('#genomeName').getValue();
        if (!genomeName) {
            Ext4.Msg.alert('Error', 'Must enter the name for this genome within the site');
            return;
        }

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'loadNcbiGenome'),
            jsonData: {
                folder: folder,
                genomePrefix: genomePrefix,
                genomeName: genomeName,
                species: species
            },
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(){
                Ext4.Msg.alert('Success', 'Job started!', function(){
                    this.close();
                    window.location = LABKEY.ActionURL.buildURL('pipeline', 'begin');
                }, this);
            }
        });
    }
});