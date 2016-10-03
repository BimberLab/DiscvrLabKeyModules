package org.labkey.sequenceanalysis;

import org.apache.log4j.Logger;
import org.labkey.api.search.AbstractDocumentParser;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.sequenceanalysis.util.SequenceUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lucene document parser
 * Created by bimber on 3/28/2016.
 */
public class SequenceNoOpDocumentParser extends AbstractDocumentParser
{
    private static final Logger _log = Logger.getLogger(SequenceNoOpDocumentParser.class);

    @Override
    protected void parseContent(InputStream stream, ContentHandler handler) throws IOException, SAXException
    {
        // Intentionally no-op as the content isn't very interesting for full text search and might be enormous
        //_log.info("skipping indexing of sequence document");
    }

    public String getMediaType()
    {
        return "text/plain";
    }

    public boolean detect(WebdavResource resource, String contentType, byte[] buf) throws IOException
    {
        return SequenceUtil.FILETYPE.fastq.getFileType().isType(resource.getFile()) ||
                SequenceUtil.FILETYPE.fastq.getFileType().isType(resource.getFile()) ||
                SequenceUtil.FILETYPE.gtf.getFileType().isType(resource.getFile()) ||
                SequenceUtil.FILETYPE.gff.getFileType().isType(resource.getFile()) ||
                SequenceUtil.FILETYPE.bed.getFileType().isType(resource.getFile()) ||
                SequenceUtil.FILETYPE.vcf.getFileType().isType(resource.getFile()) ||
                isUnderAnalysisDir(resource);
    }

    private boolean isUnderAnalysisDir(WebdavResource resource)
    {
        return resource.getFile() != null && (resource.getFile().getPath().contains("/sequenceAnalysis/") ||
                resource.getFile().getPath().contains("/analyzeAlignment/") ||
                resource.getFile().getPath().contains("/sequenceOutputPipeline/"));
    }
}

