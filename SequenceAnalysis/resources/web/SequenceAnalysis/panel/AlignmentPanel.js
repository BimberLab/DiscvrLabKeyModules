/**
 * @cfg toolConfig The JSON provided by a call to getAnalysisToolDetails
 */
Ext4.define('SequenceAnalysis.panel.AlignmentPanel', {
    extend: 'SequenceAnalysis.panel.AnalysisSectionPanel',
    alias: 'widget.sequenceanalysis-alignmentpanel',

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Step 3: Alignment (optional)',
            //sectionDescription: 'Use the field below to select the aligner.  Each aligner may have different characteristics and will be more appropriate for certain applications.',
            itemId: 'alignmentPanel',
            items: this.getItems()
        });

        this.callParent(arguments);
    },

    getItems: function(){
        return [{
            xtype: 'checkbox',
            fieldLabel: 'Perform Alignment',
            name: 'doAlignment',
            itemId: 'doAlignment',
            isToolParam: true,
            checked: true,
            listeners: {
                change: this.onCheckChange,
                afterrender: {
                    fn: this.onCheckChange,
                    scope: this,
                    delay: 100
                },
                scope: this
            }
        }];
    },

    onCheckChange: function(btn, val, oldVal){
        val = btn.getValue();
        var panel = btn.up('#alignmentPanel');
        panel.items.each(function(item){
            if (item.name != 'doAlignment'){
                panel.remove(item);
            }
        }, this);

        if (val){
            panel.add({
                xtype: 'displayfield',
                style: 'margin-top: 10px;',
                fieldLabel: 'Reference Genome'
            },{
                xtype: 'sequenceanalysis-analysissectionpanel',
                stepType: 'referenceLibraryCreation',
                comboLabel: 'Reference Genome Type',
                singleTool: true,
                toolConfig: this.toolConfig,
                comboValue: 'SavedLibrary',
                getItems: function(toolConfig){
                    var items = SequenceAnalysis.panel.AnalysisSectionPanel.prototype.getItems.call(this, toolConfig);
                    items = Ext4.Array.insert(items, 1, [{
                        xtype: 'checkbox',
                        fieldLabel: 'Copy Files To Working Directory?',
                        checked: true,
                        name: 'copyGenomeLocally'
                    }]);

                    return items;
                }
            },{
                xtype: 'displayfield',
                style: 'margin-top: 10px;',
                fieldLabel: 'Aligner Settings'
            },{
                xtype: 'sequenceanalysis-analysissectionpanel',
                toolConfig: this.toolConfig,
                stepType: 'alignment',
                comboLabel: 'Choose Aligner',
                singleTool: true,
                border: false
            },{
                xtype: 'displayfield',
                style: 'margin-top: 10px;',
                fieldLabel: 'BAM Post-Processing'
            },{
                xtype: 'sequenceanalysis-analysissectionpanel',
                toolConfig: this.toolConfig,
                stepType: 'bamPostProcessing',
                sectionDescription: 'The steps below will act on the BAM file(s).  They can be used to filter reads, sort the alignments, correct alignment information, etc.  They will be executed in the order selected.'
            });
        }
    }
});
