Ext4.define('SequenceAnalysis.panel.BamHaplotypePanel', {
    extend: 'Ext.panel.Panel',

    initComponent: function(){
        Ext4.apply(this, {
            border: false,
            width: '100%',
            defaults: {
                bodyStyle: 'padding-bottom: 20px;'
            },
            items: [{
                title: 'Instructions',
                border: true,
                html: 'This page is designed to reconstruct NT haplotypes across short region of interest from BAM alignments.  It uses read data (including paired read information) to rebuild local halotypes, and presents all distinct NT sequences across the region.  ' +
                        'It will respect INDELs, provides a relatively simple model to filter out SNPs based on quality.  It will then output all distinct sequences across this region, including their frequency.  It was originally built to study haplotypes across short regions of interest, such cytotoxic T-lymphocyte epitopes, compensatory mutations or drug resistance.' +
                        'This report is meant for research only and is provided without any warranty.<p>',
                style: 'margin-bottom: 20px;'
            }, this.getFilterConfig(), {
                title: 'Results',
                border: true,
                bodyStyle: 'padding: 5px;',
                minHeight: 200,
                items: [{
                    xtype: 'ldk-contentresizingpanel',
                    overflowX: 'auto',
                    overflowY: 'auto',
                    itemId: 'results',
                    items: [{
                        html: 'Nothing selected yet'
                    }],
                    defaults: {
                        border: false
                    }
                }]
            }]
        });

        this.callParent(arguments);
    },

    getFilterConfig: function(){
        return {
            xtype: 'form',
            title: 'Filter Options',
            style: 'padding-bottom: 20px;',
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'ldk-integerfield',
                itemId: 'minQual',
                fieldLabel: 'Min Quality For SNP',
                labelWidth: 150,
                width: 400,
                value: 10
            },{
                xtype: 'checkbox',
                itemId: 'requireCompleteCoverage',
                fieldLabel: 'Require Complete Coverage',
                labelWidth: 150,
                width: 400,
                checked: true
            },{
                xtype: 'textarea',
                itemId: 'regions',
                fieldLabel: 'Region(s)',
                labelWidth: 150,
                width: 400,
                height: 100,
                helpPopup: 'Enter one region per line, in the format: chr01:100-200',
                value: null,
                validator: function(val){
                    if (val === null || val === ''){
                        return true;
                    }

                    if (val.match(/ /g)){
                        return 'The value cannot contain spaces';
                    }

                    var tokens = val.split('\n');
                    var ret = true;
                    Ext4.Array.forEach(tokens, function(t){
                        if (!t.match(/^(.)+:([0-9])+-([0-9])+$/)){
                            ret =  'Invalid interval: [' + t + ']';
                            return false;
                        }
                    }, this);

                    return ret;
                }
            },{
                xtype: 'button',
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            }]
        }
    },

    onSubmit: function(){
        var minQual = this.down('#minQual').getValue();
        var requireCompleteCoverage = this.down('#requireCompleteCoverage').getValue();
        var regionsField = this.down('#regions');
        if (!regionsField.isValid()){
            Ext4.Msg.alert('Error', 'Invalid Regions Selected');
            return;
        }

        var regions = regionsField.getValue();
        if (!regions){
            Ext4.Msg.alert('Error', 'No Regions Provided');
            return;
        }

        regions = regions.split('\n');

        var params = {
            outputFileIds: this.outputFileIds,
            regions: regions,
            mode: 'nt',
            requireCompleteCoverage: requireCompleteCoverage
        };

        if (minQual){
            params.minQual = minQual;
        }

        Ext4.Msg.wait('Loading...');

        LABKEY.Ajax.request({
            method: 'POST',
            timeout: 9999999,
            url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'getBamHaplotypes'),
            jsonData: params,
            scope: this,
            success: LABKEY.Utils.getCallbackWrapper(this.onSuccess, this),
            failure: LDK.Utils.getErrorCallback()
        });
    },

    onSuccess: function(results){
        Ext4.Msg.hide();

        var target = this.down('#results');
        target.removeAll();

        var html = '';

        var sampleMap = results.sampleNames;

        Ext4.each(results.intervals, function(interval, intervalIdx){
            var i = interval.intervals;

            //sort by total
            var totalForAll = 0;
            var arr = [];
            for (var s in i) {
                var total = 0;
                Ext4.each(this.outputFileIds, function(id){
                    if (i[s][id]){
                        total += i[s][id];
                    }
                }, this);

                arr.push({
                    sequence: s,
                    samples: i[s],
                    total: total
                });

                totalForAll += total;
            }

            arr.sort(function(a, b){
                if (a.total == b.total)
                    return 0;
                else if (a.total > b.total)
                    return -1;
                else
                    return 1;
            });

            html += 'Interval: ' + interval.name + '<p></p><table border="1" style="font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;border-collapse:collapse;"><tr><td></td><td colspan="' + (1 + this.outputFileIds.length) + '" style="text-align:center;">Samples/Read Counts (' + totalForAll + ')</td></tr>';

            html += '<tr><td style="font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;border-collapse:collapse;">' + interval.referenceSequence + '</td><td>Total</td>';
            Ext4.each(this.outputFileIds, function(id){
                html += '<td style="padding: 5px;text-align:center;">' + sampleMap[id] + '</td>';
            }, this);
            html += '</tr>';

            Ext4.each(arr, function(data){
                console.log(data);
                html += '<tr><td style="font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;border-collapse:collapse;">' + data.sequence + '</td>';
                html += '<td style="padding: 5px;text-align:center;">' + (data.total) + '</td>';
                Ext4.each(this.outputFileIds, function(id){
                    var pct = (data.samples[id] ? ' (' + Ext4.util.Format.number(((data.samples[id] / totalForAll) * 100), '0.00') + '%)' : '');
                    html += '<td style="padding: 5px;text-align:center;">' + (data.samples[id] ? data.samples[id] : '') + pct + '</td>';
                }, this);

                html += '</tr>';
            }, this);

            html += '</table><br>';
        }, this);

        var el = Ext4.get(target.renderTarget);
        el.update(html);

        var size = el.getSize();
        this.setWidth(size.width + 20);
        target.setSize({
            width: size.width + 10,
            height: size.height + 20
        });

        this.doLayout();
        console.log(results);
    }
});