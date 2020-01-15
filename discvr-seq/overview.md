## DISCVR-Seq Overview
DISCVR-Seq is a sub-project of [DISCVR](../discvr/overview.md), designed for management and analysis of next-generation sequence data.

The core of DISCVR-Seq is the SequenceAnalysis module, and the BLAST and JBrowse modules are optional extensions. The overall goal of DISCVR-Seq is to provide a friendly, web-based way for both bioinformaticians and non-informatician scientists to manage and analyze sequence data. Our goal is to allow complex tasks to be performed without the need to use the command line. This module places heavy focus on managing samples and their metadata. Once raw data is imported once, it can be repeatedly re-analyzed without the need to re-enter sample details. The system also keeps thorough records of analysis parameters and makes it easy to repeat an analysis on new data.  DISCVR is open-source, with the code hosted in the main [Github repository](https://github.com/BimberLab/DiscvrLabKeyModules).

The primary features are:

- [Management of raw data, metadata and outputs](management.md)

- [Analysis of data, leveraging LabKey Server's pipeline to provide background processing, a job queue and optional remote processing on dedicated servers](analysis.md)

- [Management and versioning of reference genomes, tracks and non-published reference sequence data](genomes.md)

- [Integration with BLAST](blast.md)

- [Integration with the JBrowse Genome Browser](jbrowse.md)
