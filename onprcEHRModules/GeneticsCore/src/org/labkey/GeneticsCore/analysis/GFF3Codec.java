package org.labkey.GeneticsCore.analysis;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.AsciiFeatureCodec;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.LineIterator;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by bimber on 3/14/2017.
 */
public class GFF3Codec extends AsciiFeatureCodec<GFF3Feature>
{
    public static final String GFF_EXTENSION = ".gff";
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t|( +)");

    public GFF3Codec()
    {
        super(GFF3Feature.class);
    }

    public GFF3Feature decodeLoc(String line)
    {
        return this.decode(line);
    }

    public GFF3Feature decode(String line)
    {
        if (line.trim().isEmpty())
        {
            return null;
        }
        else if (!line.startsWith("#"))
        {
            String[] tokens = SPLIT_PATTERN.split(line, -1);
            return this.decode(tokens);
        }
        else
        {
            readHeaderLine(line);
            return null;
        }
    }

    public Object readActualHeader(LineIterator reader) 
    {
        return null;
    }

    public GFF3Feature decode(String[] tokens) 
    {
        int tokenCount = tokens.length;
        if (tokenCount < 9)
        {
            return null;
        } 
        else 
        {
            String chr = tokens[0];
            int start = Integer.parseInt(tokens[3]);
            int end = Integer.parseInt(tokens[4]);

            GFF3FeatureImpl feature = new GFF3FeatureImpl(chr, start, end);

            feature.setSource(tokens[1]);
            feature.setType(tokens[2]);
            feature.setScore(Float.parseFloat(tokens[5]));

            char strand = tokens[6].isEmpty() ? 32 : tokens[6].charAt(0);
            if (strand == 45)
            {
                feature.setStrand(Strand.NEGATIVE);
            }
            else if(strand == 43)
            {
                feature.setStrand(Strand.POSITIVE);
            }
            else
            {
                feature.setStrand(Strand.NONE);
            }

            try
            {
                feature.setPhase(Integer.parseInt(tokens[7]));
            }
            catch (NumberFormatException e)
            {
                //ignore
            }

            String attributes = tokens[8];
            Map<String, String> attrMap = new HashMap<>();
            if (!attributes.isEmpty())
            {
                String[] attrs = attributes.split(";");
                for (String pair : attrs)
                {
                    pair = StringUtils.trimToNull(pair);
                    if (pair == null)
                    {
                        continue;
                    }

                    String delim = "=";
                    if (!pair.contains("=") && pair.contains(":"))
                    {
                        //not official GFF3 spec, but keep to handle legacy bismark GFFs
                        delim = ":";
                    }

                    String[] parts = pair.split(delim);
                    if (parts.length != 2)
                    {
                        continue;
                    }

                    attrMap.put(parts[0], parts[1]);
                }

                feature.addAttributes(attrMap);
            }

            return feature;
        }
    }

    protected boolean readHeaderLine(String line)
    {
        return false;
    }

    public boolean canDecode(String path)
    {
        String toDecode;
        if (AbstractFeatureReader.hasBlockCompressedExtension(path))
        {
            toDecode = path.substring(0, path.lastIndexOf("."));
        }
        else
        {
            toDecode = path;
        }

        return toDecode.toLowerCase().endsWith(".gff");
    }

    public TabixFormat getTabixFormat()
    {
        return TabixFormat.GFF;
    }
}

