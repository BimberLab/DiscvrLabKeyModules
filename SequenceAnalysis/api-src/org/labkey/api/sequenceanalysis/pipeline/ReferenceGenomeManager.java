package org.labkey.api.sequenceanalysis.pipeline;

import com.google.common.io.Files;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ReferenceGenomeManager
{
    private static final ReferenceGenomeManager _instance = new ReferenceGenomeManager();

    private ReferenceGenomeManager()
    {

    }

    public static ReferenceGenomeManager get()
    {
        return _instance;
    }

    private File getLocalUpdateFile(ReferenceGenome genome)
    {
        return new File(genome.getSourceFastaFile().getParentFile(), ".lastUpdate");
    }

    private File getRemoteSyncFile(int genomeId)
    {
        File remoteDir = new File(SequencePipelineService.get().getRemoteGenomeCacheDirectory(), String.valueOf(genomeId));

        return new File(remoteDir, ".lastSync");
    }

    private boolean isUpToDate(ReferenceGenome genome)
    {
        File localFile = getLocalUpdateFile(genome);
        if (!localFile.exists())
        {
            return false;
        }

        File remoteFile = getRemoteSyncFile(genome.getGenomeId());
        if (!remoteFile.getParentFile().exists())
        {
            return false;
        }

        if (!remoteFile.exists())
        {
            return false;
        }

        long lastUpdated = localFile.lastModified();
        long lastSync = remoteFile.lastModified();

        return lastSync >= lastUpdated;
    }

    public void markGenomeModified(ReferenceGenome genome, Logger log) throws IOException
    {
        File toUpdate = getLocalUpdateFile(genome);
        log.info("Marking genome as modified: " + toUpdate.getPath());
        Files.touch(toUpdate);
    }

    public void cacheGenomeLocally(ReferenceGenome genome, Logger log) throws PipelineJobException
    {
        if (!SequencePipelineService.get().isRemoteGenomeCacheUsed())
        {
            return;
        }

        if (genome.isTemporaryGenome())
        {
            log.info("cannot cache custom genomes, skipping");
            return;
        }

        if (isUpToDate(genome))
        {
            log.debug("Genome up-to-date, will not repeat rsync");
            return;
        }

        File localCacheDir = SequencePipelineService.get().getRemoteGenomeCacheDirectory();
        log.info("attempting to rsync genome to local disks: " + localCacheDir.getPath());

        File sourceDir = genome.getSourceFastaFile().getParentFile();

        //Note: neither source nor dest have trailing slashes, so the entire source (i.e '128', gets synced into a subdir of dest)
        new SimpleScriptWrapper(log).execute(Arrays.asList(
                "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", sourceDir.getPath(), localCacheDir.getPath()
        ));

        try
        {
            File lastUpdate = getLocalUpdateFile(genome);
            if (!lastUpdate.exists())
            {
                Files.touch(lastUpdate);
            }

            Files.touch(getRemoteSyncFile(genome.getGenomeId()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        genome.setWorkingFasta(new File(new File(localCacheDir, genome.getGenomeId().toString()), genome.getSourceFastaFile().getName()));
    }
}
