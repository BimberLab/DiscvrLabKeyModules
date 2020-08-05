/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 2:57 PM
 */
public class NtSnpByPosAggregator extends AbstractAlignmentAggregator
{
    private NtCoverageAggregator _coverageAggregator = null;
    private boolean _coverageTrackedExternally;
    private Map<String, Integer> _snps = new HashMap<>();
    private Map<String, String> _settings;
    private int _totalFilteredSnps = 0;
    private int _totalAlignments = 0;

    public NtSnpByPosAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgQualAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgQualAggregator, settings);
        _settings = settings;
    }

    public NtCoverageAggregator getCoverageAggregator()
    {
        if (_coverageAggregator == null)
            _coverageAggregator = new NtCoverageAggregator(getLogger(), getRefFasta(), _avgQualAggregator, _settings);

        return _coverageAggregator;
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps) throws PipelineJobException
    {
        //NOTE: depth is handled by superclass
        if (!_coverageTrackedExternally)
        {
            getCoverageAggregator().inspectAlignment(record, ref, snps);
        }

        if (!isPassingAlignment(record, true))
        {
            return;
        }

        _totalAlignments++;

        for (Integer pos : snps.keySet())
        {
            for (NTSnp snp : snps.get(pos))
            {
                inspectNtSnp(snp, record, ref);
            }
        }
    }

    protected void inspectNtSnp(NTSnp snp, SAMRecord record, ReferenceSequence ref)throws PipelineJobException
    {
        String key = getSNPKey(snp);

        //TODO: account for overlapping paired end reads?  perhaps cache under the first mate's name?
        if (snp.getReadBase() != snp.getReferenceBase(ref.getBases()))
        {
            if (!isPassingSnp(record, snp))
            {
                _totalFilteredSnps++;
                return;
            }

            Integer count = _snps.get(key);
            if (count == null)
                count = 1;
            else
                count++;
            _snps.put(key, count);
        }

        if (_cacheDef.get(key) == null)
        {
            _cacheDef.put(key, new CacheKeyInfo(snp, ref));
        }
    }

    private String getSNPKey(NTSnp snp)
    {
        return new StringBuilder(snp.getPositionInfo().getReferenceName()).append("||")
            .append(snp.getLastRefPosition()).append("||")
            .append(snp.getInsertIndex()).append("||")
            .append(snp.getReadBaseString()).toString();
    }

    @Override
    public void writeOutput(User u, Container c, AnalysisModel model)
    {
        getLogger().info("Saving NT SNP results");
        Map<String, Integer> summary = new HashMap<>();

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            //delete existing records
            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
            long deleted = Table.delete(ti, filter);

            int processed = 0;
            for (String key : _snps.keySet())
            {
                CacheKeyInfo info = _cacheDef.get(key);
                Map<String, Object> row = new HashMap<>();

                Integer refId = getReferenceLibraryHelper().resolveSequenceId(info.getRefName());

                //keep track of positions by reference
                Integer totalSaved = summary.get(info.getRefName());
                if (totalSaved == null)
                    totalSaved = 0;

                totalSaved++;
                summary.put(info.getRefName(), totalSaved);

                row.put("analysis_id", model.getAnalysisId());
                row.put("ref_nt_id", refId);
                row.put("ref_nt_name", info.getRefName());
                row.put("ref_nt_position", info.getRefPosition1()); //use 1-based
                row.put("ref_nt_insert_index", info.getInsertIndex());
                row.put("q_nt", info.getReadBase());
                row.put("readcount", _snps.get(key));

                Integer depth = getCoverageAggregator().getDepthAtPosition(info.getRefName(), info.getRefPosition(), 0); //always use depth at last non-indel position
                assert depth != null;
                row.put("depth", depth);

                if (!info.getReadBase().equals("N"))
                {
                    Integer adj_depth = getCoverageAggregator().getHcDepthAtPosition(info.getRefName(), info.getRefPosition(), 0); //always use depth at last non-indel position
                    if (adj_depth != null)
                    {
                        Double pct = adj_depth == 0 ? 0 : ((double) _snps.get(key) / adj_depth ) * 100.0;
                        row.put("adj_depth", adj_depth);
                        row.put("pct", pct);
                    }
                    else
                    {
                        getLogger().error("No adjusted depth found for: " + info.getCoverageKey());
                    }
                }
                row.put("ref_nt", info.getRefBase());

                //TODO: pvalue

                row.put("container", c.getEntityId());
                row.put("createdby", u.getUserId());
                row.put("modifiedby", u.getUserId());
                row.put("created", new Date());
                row.put("modified", new Date());

                Table.insert(u, ti, row);

                processed++;
                if (_logProgress && processed % 5000 == 0)
                {
                    getLogger().info("processed " + processed + " positions for DB insert in NTSnpByPosAggregator");
                }
            }

            transaction.commit();

            getLogger().info("\tReference sequences saved: " + summary.keySet().size());
            getLogger().info("\tTotal filtered SNPs: " + _totalFilteredSnps);
            getLogger().info("\tTotal alignments inspected: " + _totalAlignments);
            getLogger().info("\tSNPs saved by reference:");
            for (String refId : summary.keySet())
            {
                getLogger().info("\t" + refId + ": " + summary.get(refId));
            }
        }
    }

    public void setCoverageAggregator(NtCoverageAggregator coverageAggregator, boolean coverageTrackedExternally)
    {
        _coverageAggregator = coverageAggregator;
        _coverageTrackedExternally = coverageTrackedExternally;
    }

    @Override
    public String getSynopsis()
    {
        return "NT SNP By Pos Aggregator:\n" +
                "\tMinMapQual: " + getMinMapQual() + "\n" +
                "\tMinSnpQual: " + getMinSnpQual() + "\n" +
                "\tMinAvgSnpQual: " + getMinAvgSnpQual() + "\n" +
                "\tMinDipQual: " + getMinDipQual() + "\n" +
                "\tMinAvgDipQual: " + getMinAvgDipQual() + "\n"
                ;
    }
}