package org.labkey.jbrowse;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JBrowseFieldUtils
{
    private static final Logger _log = LogHelper.getLogger(JBrowseFieldUtils.class, "Logger for JBrowseFieldUtils");

    public static final String VARIABLE_SAMPLES = "variableSamples";
    public static final String N_HET = "nHet";
    public static final String N_HOMVAR = "nHomVar";
    public static final String N_CALLED = "nCalled";
    public static final String FRACTION_HET = "fractionHet";


    // These fields are always indexed in DISCVRSeq, and present in all VCFs (or created client-side in ExtendedVariantAdapter
    public static final Map<String, JBrowseFieldDescriptor> DEFAULT_FIELDS = new LinkedHashMap<>() {{
        put("contig", new JBrowseFieldDescriptor("contig", "This is the chromosome/contig", true, true, VCFHeaderLineType.String, 1).label("Chromosome"));
        put("start", new JBrowseFieldDescriptor("start", "The start position of this variant", true, true, VCFHeaderLineType.Integer, 2).label("Start"));
        put("end", new JBrowseFieldDescriptor("end", "The end position of this variant", false, true, VCFHeaderLineType.Integer, 3).label("End"));
        put("ref", new JBrowseFieldDescriptor("ref", "The reference allele", true, true, VCFHeaderLineType.String, 4).label("Ref Allele"));
        put("alt", new JBrowseFieldDescriptor("alt", "The alternate allele", true, true, VCFHeaderLineType.String, 5).label("Alt Allele"));
        put("genomicPosition", new JBrowseFieldDescriptor("genomicPosition", "", false, true, VCFHeaderLineType.Integer, 6).hidden(true).label("Genomic Position"));
    }};

    public static Map<String, JBrowseFieldDescriptor> getIndexedFields(JsonFile jsonFile, User u, Container c)
    {
        Map<String, JBrowseFieldDescriptor> ret = new LinkedHashMap<>(DEFAULT_FIELDS);
        ret.putAll(getGenotypeDependentFields(jsonFile));

        File vcf = jsonFile.getTrackFile();
        if (!vcf.exists())
        {
            throw new IllegalArgumentException("Unable to find file: " + vcf.getPath());
        }

        try (VCFFileReader reader = new VCFFileReader(vcf))
        {
            VCFHeader header = reader.getFileHeader();
            for (String fn : jsonFile.getInfoFieldsToIndex())
            {
                if (!header.hasInfoLine(fn))
                {
                    _log.error("Field requested for JBrowse indexing, but was not present: " + fn + ", for JsonFile: " + jsonFile.getObjectId());
                    continue;
                }

                VCFInfoHeaderLine info = header.getInfoHeaderLine(fn);
                JBrowseFieldDescriptor fd = new JBrowseFieldDescriptor(fn, info.getDescription(), false, true, info.getType(), 8);

                JBrowseServiceImpl.get().customizeField(u, c, fd);
                ret.put(fn, fd);
            }
        }

        return ret;
    }

    public static Map<String, JBrowseFieldDescriptor> getGenotypeDependentFields(@Nullable JsonFile jsonFile) {
        Map<String, JBrowseFieldDescriptor> ret = new HashMap<>();
        ret.put(VARIABLE_SAMPLES, new JBrowseFieldDescriptor(VARIABLE_SAMPLES, "All samples with this variant", true, true, VCFHeaderLineType.Character, 7).multiValued(true).label("Samples With Variant"));

        // TODO: restore these once existing indexes are regenerated:
        // ret.put(N_HET, new JBrowseFieldDescriptor(N_HET, "The number of samples with this allele that are heterozygous", false, true, VCFHeaderLineType.Integer, 9).label("# Heterozygotes"));
        // ret.put(N_HOMVAR, new JBrowseFieldDescriptor(N_HOMVAR, "The number of samples with this allele that are homozygous", false, true, VCFHeaderLineType.Integer, 9).label("# Homozygous Variant"));
        // ret.put(N_CALLED, new JBrowseFieldDescriptor(N_CALLED, "The number of samples with called genotypes at this position", false, true, VCFHeaderLineType.Integer, 9).label("# Genotypes Called"));
        // ret.put(FRACTION_HET, new JBrowseFieldDescriptor(FRACTION_HET, "The fraction of samples with this allele that are heterozygous", false, true, VCFHeaderLineType.Float, 9).label("Fraction Heterozygotes"));

        if (jsonFile != null) {
            File vcf = jsonFile.getTrackFile();
            if (vcf == null || !vcf.exists()) {
                String msg = "Unable to find VCF file for track: " + jsonFile.getObjectId();
                _log.error(msg + ", expected: " + (vcf == null ? "null" : vcf.getPath()));
            }
            else
            {
                try (VCFFileReader reader = new VCFFileReader(vcf))
                {
                    VCFHeader header = reader.getHeader();
                    if (!header.hasGenotypingData())
                    {
                        ret.clear();
                    }
                    else
                    {
                        ret.get(VARIABLE_SAMPLES).allowableValues(header.getSampleNamesInOrder());
                    }
                }
            }
        }

        return ret;
    }


    public static JBrowseSession getSession(String sessionId)
    {
        JBrowseSession session = JBrowseSession.getForId(sessionId);
        if (session == null)
        {
            throw new IllegalArgumentException("Unable to find JBrowse session: " + sessionId);
        }

        return session;
    }

    public static JsonFile getTrack(JBrowseSession session, String trackId, User u)
    {
        JsonFile track = session.getTrack(u, trackId);
        if (track == null)
        {
            throw new IllegalArgumentException("Unable to find track with ID: " + trackId);
        }

        if (!track.shouldHaveFreeTextSearch())
        {
            throw new IllegalArgumentException("This track does not support free text search: " + trackId);
        }

        if (!track.doExpectedSearchIndexesExist())
        {
            throw new IllegalArgumentException("The lucene index has not been created for this track: " + trackId);
        }

        return track;
    }
}
