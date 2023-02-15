/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 2:44 PM
 */
abstract public class AbstractAlignmentStepProvider<StepType extends AlignmentStep> extends AbstractPipelineStepProvider<StepType> implements AlignmentStepProvider
{
    public static String ALIGNMENT_MODE_PARAM = "alignmentMode";
    public static String SUPPORT_MERGED_UNALIGNED = "supportsMergeUnaligned";
    public static String COLLECT_WGS_METRICS = "collectWgsMetrics";
    public static String CONVERT_TO_CRAM = "convertToCram";
    public static String COLLECT_WGS_METRICS_NON_ZERO = "collectWgsMetricsNonZero";
    public static String DISCARD_BAM = "discardBam";

    public enum ALIGNMENT_MODE
    {
        ALIGN_THEN_MERGE(),
        MERGE_THEN_ALIGN();
    }

    private boolean _supportsPairedEnd;
    private boolean _supportsMergeUnaligned;
    private boolean _alwaysCacheIndex;

    public AbstractAlignmentStepProvider(String name, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL, boolean supportsPairedEnd, boolean supportsMergeUnaligned)
    {
        this(name, description, parameters, clientDependencyPaths, websiteURL, supportsPairedEnd, supportsMergeUnaligned, ALIGNMENT_MODE.ALIGN_THEN_MERGE);
    }

    public AbstractAlignmentStepProvider(String name, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL, boolean supportsPairedEnd, boolean supportsMergeUnaligned, ALIGNMENT_MODE alignmentMode)
    {
        this(name, description, parameters, clientDependencyPaths, websiteURL, supportsPairedEnd, supportsMergeUnaligned, true, alignmentMode);
    }

    public AbstractAlignmentStepProvider(String name, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL, boolean supportsPairedEnd, boolean supportsMergeUnaligned, boolean supportAlignmentMetrics, ALIGNMENT_MODE alignmentMode)
    {
        super(name, name, name, description, getParamList(parameters, supportsMergeUnaligned, supportAlignmentMetrics, alignmentMode), getDependencies(clientDependencyPaths), websiteURL);

        _supportsPairedEnd = supportsPairedEnd;
        _supportsMergeUnaligned = supportsMergeUnaligned;
        _alwaysCacheIndex = false;
    }

    public boolean isAlwaysCacheIndex()
    {
        return _alwaysCacheIndex;
    }

    public void setAlwaysCacheIndex(boolean alwaysCacheIndex)
    {
        _alwaysCacheIndex = alwaysCacheIndex;
    }

    private static List<ToolParameterDescriptor> getParamList(List<ToolParameterDescriptor> list, boolean supportsMergeUnaligned, boolean supportAlignmentMetrics, ALIGNMENT_MODE alignmentMode)
    {
        List<ToolParameterDescriptor> parameters = new ArrayList<>();
        if (list != null)
        {
            parameters.addAll(list);
        }

        if (supportsMergeUnaligned)
        {
            parameters.add(ToolParameterDescriptor.create(SUPPORT_MERGED_UNALIGNED, "Merge Unaligned Reads", "If checked, the pipeline will attempt to merge unaligned reads into the final BAM file.  This is generally a good idea since it ensures information is not lost; however, in some situations you may know upfront that you do not need these reads.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true));
        }
        else
        {
            parameters.add(ToolParameterDescriptor.create(SUPPORT_MERGED_UNALIGNED, "Merge Unaligned Reads", "If checked, the pipeline will attempt to merge unaligned reads into the final BAM file.  This is generally a good idea since it ensures information is not lost; however, in some situations you may know upfront that you do not need these reads.", "hidden", null, false));
        }

        if (supportAlignmentMetrics)
        {
            parameters.add(ToolParameterDescriptor.create(COLLECT_WGS_METRICS, "Collect WGS Metrics", "If checked, the pipeline will run Picard tool CollectWgsMetrics, which gathers various metrics including coverage depth.", "checkbox", new JSONObject()
            {{
                put("checked", true);
            }}, true));

            parameters.add(ToolParameterDescriptor.create(COLLECT_WGS_METRICS_NON_ZERO, "Collect WGS Metrics Over Non-Zero Coverage", "If checked, the pipeline will run Picard tool CollectWgsMetrics, which gathers various metrics including coverage depth over positions of non-zero coverage.", "checkbox", new JSONObject()
            {{
                put("checked", false);
            }}, true));
        }

        parameters.add(ToolParameterDescriptor.create(DISCARD_BAM, "Discard BAM", "If checked, the pipeline will discard the alignment (BAM file) at the end of this pipeline.  This is primarily used if your pipeline calculates some data from this BAM and you do not need to keep the BAM itself for disk space reasons.", "checkbox", new JSONObject(){{
            put("checked", false);
        }}, false));

        parameters.add(ToolParameterDescriptor.create(CONVERT_TO_CRAM, "Convert to CRAM", "If checked, the final step of the pipeline will convert the BAM file to CRAM to save space.", "checkbox", new JSONObject(){{
            put("checked", false);
        }}, false));

        parameters.add(ToolParameterDescriptor.create(ALIGNMENT_MODE_PARAM, "Alignment Mode", "If your readset has more than one pair of FASTQs, there pipeline can either align each pair sequentially (and then merge these BAMs), or merge the pairs of FASTQs first and then perform alignment once.  The default is to align each pair of FASTQs separately; however, some pipelines like STAR require the latter.", "ldk-simplecombo", new JSONObject(){{
            put("storeValues", ALIGNMENT_MODE.ALIGN_THEN_MERGE.name() + ";" + ALIGNMENT_MODE.MERGE_THEN_ALIGN.name());
            put("value", alignmentMode.name());
        }}, true));

        return parameters;
    }

    @Override
    public boolean supportsMergeUnaligned()
    {
        return _supportsMergeUnaligned;
    }

    private static LinkedHashSet<String> getDependencies(Collection<String> input)
    {
        LinkedHashSet<String> ret = new LinkedHashSet<>();
        ret.add("/ldk/field/SimpleCombo.js");
        if (input != null)
        {
            ret.addAll(input);
        }

        return ret;
    }

    public boolean supportsPairedEnd()
    {
        return _supportsPairedEnd;
    }

    public boolean isSupportsMergeUnaligned()
    {
        return _supportsMergeUnaligned;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = super.toJSON();
        json.put("supportsPairedEnd", supportsPairedEnd());
        json.put("supportsMergeUnaligned", isSupportsMergeUnaligned());
        json.put("alwaysCacheIndex", isAlwaysCacheIndex());

        return json;
    }
}
