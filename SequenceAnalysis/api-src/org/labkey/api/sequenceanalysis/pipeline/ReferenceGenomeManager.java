package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
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

        return lastUpdated >= lastSync;
    }

    public void markGenomeModified(ReferenceGenome genome) throws IOException
    {
        File toUpdate = getLocalUpdateFile(genome);
        FileUtils.touch(toUpdate);
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

        new SimpleScriptWrapper(log).execute(Arrays.asList(
                "rsync", "-r", "-vi", "-a", "--delete", "--delete-excluded", "--no-owner", "--no-group", sourceDir.getPath(), localCacheDir.getPath()
        ));

        try
        {
            File lastUpdate = getLocalUpdateFile(genome);
            if (!lastUpdate.exists())
            {
                FileUtils.touch(lastUpdate);
            }

            FileUtils.touch(getRemoteSyncFile(genome.getGenomeId()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        genome.setWorkingFasta(new File(new File(localCacheDir, genome.getGenomeId().toString()), genome.getSourceFastaFile().getName()));
    }
}
