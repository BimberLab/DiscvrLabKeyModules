Ext4.define('SequenceAnalysis.window.SnpDetailsWindow', {
    extend: 'Ext.window.Window',
    statics: {
        showWindow: function(analysis_id, ref_aa_id, ref_aa_position, ref_aa_insert_index){
            Ext4.create('SequenceAnalysis.window.SnpDetailsWindow', {
                analysis_id: analysis_id,
                ref_aa_id: ref_aa_id,
                ref_aa_position: ref_aa_position,
                ref_aa_insert_index: ref_aa_insert_index
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            minHeight: 200,
            minWidth: 400,
            border: false,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false
            },
            items: [{
                html: 'Loading...'
            }],
            title: 'SNP Details',
            closeAction: 'destroy',
            buttons: [{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);

        this.loadData();
    },

    loadData: function(){
        LABKEY.Query.selectRows({
            schemaName: 'sequenceanalysis',
            queryName: 'aa_snps_by_codon',
            filterArray: [
                LABKEY.Filter.create('analysis_id', this.analysis_id),
                LABKEY.Filter.create('ref_aa_id', this.ref_aa_id),
                LABKEY.Filter.create('ref_aa_position', this.ref_aa_position),
                LABKEY.Filter.create('ref_aa_insert_index', this.ref_aa_insert_index),
                LABKEY.Filter.create('pct', SequenceAnalysis.panel.SnpAlignmentPanel.MIN_PCT, LABKEY.Filter.Types.GT)
            ],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: this.onDataLoad
        });
    },

    onDataLoad: function(results){
        this.removeAll();
        this.add(this.getItems(results.rows));
        this.center();
    },

    getItems: function(rows){
        rows.sort(function(a, b){
            return a.pct < b.pct ? 1 : a.pct == b.pct ? 0 : -1;
        });

        var toAdd = [];
        toAdd.push({html: 'AA Position'});
        toAdd.push({html: 'NT Position(s)'});
        toAdd.push({html: 'Ref AA'});
        toAdd.push({html: 'Codon'});
        toAdd.push({html: 'Translation'});
        toAdd.push({html: 'Depth'});
        toAdd.push({html: 'Reads'});
        toAdd.push({html: 'Adj Count'});
        toAdd.push({html: 'Adj Percent'});

        var totalNonWT = 0;
        var adj_depth = 0;
        Ext4.Array.forEach(rows, function(r){
            toAdd.push({html: (r.ref_aa_insert_index === 0 ? r.ref_aa_position : r.ref_aa_position + '.' + r.ref_aa_insert_index)});
            toAdd.push({html: r.ref_nt_positions});
            toAdd.push({html: r.ref_aa_insert_index === 0 ? r.ref_aa : '-'});
            toAdd.push({html: r.codon});
            toAdd.push({html: r.q_aa});
            toAdd.push({html: r.depth});
            toAdd.push({html: r.readcount});
            toAdd.push({html: r.adj_depth});
            toAdd.push({html: (r.pct ? LABKEY.Utils.roundNumber(r.pct, 2) : '')});

            totalNonWT += r.readcount;
            adj_depth = r.adj_depth;
        }, this);

        toAdd.push({html: 'Total Non-WT'});
        toAdd.push({colspan: 7, html: ''});
        toAdd.push({html: LABKEY.Utils.roundNumber(totalNonWT, 2) + " (" + LABKEY.Utils.roundNumber((100 * (totalNonWT / adj_depth)), 2) + "%)"});

        toAdd.push({html: 'Total WT'});
        toAdd.push({colspan: 7, html: ''});
        toAdd.push({html: LABKEY.Utils.roundNumber((adj_depth - totalNonWT), 2) + " (" + LABKEY.Utils.roundNumber((100 * ((adj_depth - totalNonWT) / adj_depth)), 2) + "%)"});

        return [{
            layout: {
                type: 'table',
                columns: 9,
                tableAttrs: {
                    border: 1
                },
                tdAttrs: {
                    style: 'padding: 5px;'
                }
            },
            items: toAdd,
            defaults: {
                border: false
            }
        }];
    }
});