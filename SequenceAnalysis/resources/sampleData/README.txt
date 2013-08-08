These are sample sequence files for automated tests.  Below is a brief description of each:

dualBarcodes_SIV.fastq:
This file has 454 reads that are barcoded on both ends using combinations of Roche's MID01-MID04.  The
sequences are from the virus SIVmac239.  It has been converted to FASTQ.

sample454_SIV:
This file has non-barcoded 454 reads that are still in SFF format.  The sequences are from the virus SIVmac239.

sampleOutput.seqout.xml:
This is a file in the same XML format that is created by the perl part of the sequence pipeline.  It is used
to represent the output of this pipeline (alignments and SNPs).  The SequenceAnalysis module provides a data
handler that is able to parse and import this file.

