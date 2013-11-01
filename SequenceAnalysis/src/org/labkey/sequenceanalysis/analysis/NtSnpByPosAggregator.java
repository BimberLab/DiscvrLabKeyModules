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
package org.labkey.sequenceanalysis.analysis;

import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.SAMRecord;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/22/12
 * Time: 2:57 PM
 */
public class NtSnpByPosAggregator extends AbstractAlignmentAggregator
{
    private NtCoverageAggregator _coverageAggregator = null;
    private Map<String, Integer> _snps = new HashMap<>();

    public NtSnpByPosAggregator(SequencePipelineSettings settings, Logger log, AvgBaseQualityAggregator avgQualAggregator)
    {
        super(settings, log, avgQualAggregator);
    }

    public NtCoverageAggregator getCoverageAggregator()
    {
        if (_coverageAggregator == null)
            _coverageAggregator = new NtCoverageAggregator(_settings, _log, _avgQualAggregator);

        return _coverageAggregator;
    }

    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, CigarPositionIterable cpi)
    {
        //NOTE: depth is handled by superclass
        getCoverageAggregator().inspectAlignment(record, ref, snps, cpi);

        if (record.getReadUnmappedFlag())
            return;

        for (Integer pos : snps.keySet())
        {
            for (NTSnp snp : snps.get(pos))
            {
                inspectNtSnp(snp, record, ref);
            }
        }
    }

    protected void inspectNtSnp(NTSnp snp, SAMRecord record, ReferenceSequence ref)
    {
        String key = getSNPKey(snp);
        if (_cacheDef.get(key) == null)
        {
            _cacheDef.put(key, new CacheKeyInfo(snp, ref));
        }

        //TODO: account for paired end reads?  perhaps cache under the first mate's name?
        if (snp.getReadBase() != snp.getReferenceBase(ref.getBases()))
        {
            Integer count = _snps.get(key);
            if (count == null)
                count = 1;
            else
                count++;
            _snps.put(key, count);

            evaluateSnp(record, snp);
        }
    }

    private String getSNPKey(NTSnp snp)
    {
        return new StringBuilder(snp.getPositionInfo().getReferenceName()).append("||")
            .append(snp.getLastRefPosition()).append("||")
            .append(snp.getInsertIndex()).append("||")
            .append(snp.getReadBaseString()).toString();
    }

    public void saveToDb(User u, Container c, AnalysisModel model)
    {
        _log.info("Saving NT SNP results to DB");
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

                String[] nameParts = info.getRefName().split("\\|");
                Integer refId = Integer.parseInt(nameParts[0]);

                //keep track of positions by reference
                Integer totalSaved = summary.get(nameParts[1]);
                if (totalSaved == null)
                    totalSaved = 0;

                totalSaved++;
                summary.put(nameParts[1], totalSaved);

                row.put("analysis_id", model.getAnalysisId());
                row.put("ref_nt_id", refId);
                row.put("ref_nt_name", nameParts[1]);
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
                        Double pct = ((double) _snps.get(key) / adj_depth ) * 100.0;
                        row.put("adj_depth", adj_depth);
                        row.put("pct", pct);
                    }
                    else
                    {
                        _log.error("No adjusted depth found for: " + info.getCoverageKey());
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
                    _log.info("processed " + processed + " positions for DB insert in NTSnpByPosAggregator");
                }
            }

            transaction.commit();

            _log.info("\tReference sequences saved: " + summary.keySet().size());
            _log.info("\tSNPs saved by reference:");
            for (String refId : summary.keySet())
            {
                _log.info("\t" + refId + ": " + summary.get(refId));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setCoverageAggregator(NtCoverageAggregator coverageAggregator)
    {
        _coverageAggregator = coverageAggregator;
    }

    public String getSynopsis()
    {
        return "NT SNP By Pos Aggregator:\n" +
                "\tMinSnpQual: " + _minSnpQual + "\n" +
                "\tMinAvgSnpQual: " + _minAvgSnpQual + "\n" +
                "\tMinDipQual: " + _minDipQual + "\n" +
                "\tMinAvgDipQual: " + _minAvgDipQual + "\n"
                ;
    }
}