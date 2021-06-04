package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
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

    public void markGenomeModified(ReferenceGenome genome, Logger log) throws PipelineJobException
    {
        File toUpdate = getLocalUpdateFile(genome);
        log.info("Marking genome as modified: " + toUpdate.getPath());
        touchFile(toUpdate, log);
    }

    //NOTE: Java implementations of touch are erroring between the cluster and NFS filesystem
    private void touchFile(File target, Logger log) throws PipelineJobException
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            try
            {
                FileUtils.touch(target);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(log);
            wrapper.execute(Arrays.asList("/bin/bash", "-c", "$(which touch) '" + target.getPath() + "'"));
        }
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

        File localCacheDir = SequencePipelineService.get().getRemoteGenomeCacheDirectory();
        if (isUpToDate(genome))
        {
            log.debug("Genome up-to-date, will not repeat rsync");
            genome.setWorkingFasta(new File(new File(localCacheDir, genome.getGenomeId().toString()), genome.getSourceFastaFile().getName()));

            return;
        }

        log.info("attempting to rsync genome to local disks: " + localCacheDir.getPath());

        File sourceDir = genome.getSourceFastaFile().getParentFile();

        //Note: neither source nor dest have trailing slashes, so the entire source (i.e '128', gets synced into a subdir of dest)
        new SimpleScriptWrapper(log).execute(Arrays.asList(
                "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", "--chmod=D2770,F660", sourceDir.getPath(), localCacheDir.getPath()
        ));

        File lastUpdate = getLocalUpdateFile(genome);
        if (!lastUpdate.exists())
        {
            touchFile(lastUpdate, log);
        }

        touchFile(getRemoteSyncFile(genome.getGenomeId()), log);

        genome.setWorkingFasta(new File(new File(localCacheDir, genome.getGenomeId().toString()), genome.getSourceFastaFile().getName()));
    }
}
