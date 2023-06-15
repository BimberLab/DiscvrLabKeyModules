export const FIELD_NAME_MAP = {
    AF: {
        title: 'Allele Frequency'
    },
    AC: {
        title: 'Allele Count'
    },
    ANN: {
        title: 'Predicted Impact (SnpEff)'
    },
    CADD_PH: {
        title: 'CADD Functional Prediction Score'
    },
    ENC: {
        title: 'ENCODE Class'
    },
    ENN: {
        title: 'ENCODE Feature Name'
    },
    ENCDNA_SC: {
        title: 'ENCODE DNase Sensitivity Score'
    },
    ENCTFBS_SC: {
        title: 'ENCODE Transcription Factor Binding Site Score'
    },
    ENCTFBS_TF: {
        title: 'ENCODE Transcription Factors'
    },
    ENCSEG_NM: {
        title: 'ENCODE Segmentation Status'
    },
    FS_EN: {
        title: 'Funseq Annotated?'
    },
    FS_NS: {
        title: 'Funseq Noncoding Score',
        range: [0,5.4]
    },
    FS_SN: {
        title: 'Funseq Sensitive Region?'
    },
    FS_TG: {
        title: 'Funseq Target Gene?'
    },
    FS_US: {
        title: 'Funseq Ultra-Sensitive Region?'
    },
    FS_WS: {
        title: 'Funseq Score'
    },
    FS_SC: {
        title: 'Funseq Non-coding Score',
        range: [0,6]
    },
    SF: {
        title: 'Swiss Prot Protein Function'
    },
    SX: {
        title: 'Swiss Prot Expression'
    },
    SD: {
        title: 'Swiss Prot Disease Annotations'
    },
    SM: {
        title: 'Swiss Prot Post-translational Modifications'
    },
    RFG: {
        title: 'Mutation Type (RefSeq)'
    },
    PC_PL: {
        title: 'Conservation Score Among 46 Placental Mammals (PhastCons)'
    },
    PC_PR: {
        title: 'Conservation Score Among 46 Primates (PhastCons)'
    },
    PC_VB: {
        title: 'Conservation Score Among 100 Vertebrate Species (PhastCons)'
    },
    PP_PL: {
        title: 'Conservation Score Among 46 Placental Mammals (Phylop)'
    },
    PP_PR: {
        title: 'Conservation Score Among 46 Primates (Phylop)'
    },
    PP_VB: {
        title: 'Conservation Score Among 100 Vertebrate Species (Phylop)'
    },
    RDB_WS: {
        title: 'RegulomeDB Score',
        range: [1,6]
    },
    RDB_MF: {
        title: 'RegulomeDB Motifs'
    },
    MAF: {
        title: 'Minor Allele Frequency'
    },
    OMIMN: {
        title: 'OMIM Number'
    },
    OMIMM: {
        title: 'OMIM Method'
    },
    OMIMC: {
        title: 'OMIM Comments'
    },
    OMIMMUS: {
        title: 'OMIM Mouse Correlate'
    },
    OMIMD: {
        title: 'OMIM Disorders'
    },
    OMIMS: {
        title: 'OMIM Status'
    },
    OMIMT: {
        title: 'OMIM Title'
    },
    GRASP_PH: {
        title: 'GRASP Phenotype'
    },
    GRASP_AN: {
        title: 'GRASP Ancestry'
    },
    GRASP_P: {
        title: 'GRASP p-value'
    },
    GRASP_PL: {
        title: 'GRASP Platform'
    },
    GRASP_PMID: {
        title: 'GRASP PMID'
    },
    GRASP_RS: {
        title: 'GRASP SNP RS Number'
    },
    ERBCTA_NM: {
        title: 'Ensembl Regulatory Build Predicted State'
    },
    LF: {
        title: 'Unable to Lift to Human'
    },
    NE: {
        title: 'PolyPhen2 Score'
    },
    NF: {
        title: 'PolyPhen2 Prediction'
    },
    NG: {
        title: 'nsdb LRT Score'
    },
    NH: {
        title: 'nsdb LRT Prediction'
    },
    NK: {
        title: 'MutationAssessor Score'
    },
    NL: {
        title: 'MutationAssessor Prediction'
    },
    NC: {
        title: 'nsdb SIFT Score'
    },
    NJ: {
        title: 'MutationTaster Prediction'
    },
    LOF: {
        title: 'Predicted Loss-of-function'
    },
    FC: {
        title: 'Probable Promoter (FAMTOM5)'
    },
    FE: {
        title: 'Probable Enhancer (FANTOM5)'
    },
    TMAF: {
        title: '1000Genomes Allele Frequency'
    },
    CLN_SIG: {
        title: 'ClinVar Significance'
    },
    CLN_DN: {
        title: 'ClinVar Disease/Concept'
    },
    CLN_DNINCL: {
        title: 'ClinVar Disease/Concept'
    },
    CLN_ALLELE: {
        title: 'ClinVar Allele'
    },
    CLN_ALLELEID: {
        title: 'ClinVar Allele ID'
    },
    CLN_VI: {
        title: 'ClinVar Sources'
    },
    CLN_DBVARID: {
        title: 'ClinVar dbVAR Accessions'
    },
    CLN_GENEINFO: {
        title: 'ClinVar Gene(s)'
    },
    CLN_RS: {
        title: 'ClinVar RS Numbers'
    },
    LiftedContig: {
        title: 'Contig (Lifted to Human)'
    },
    LiftedStart: {
        title: 'Start (Lifted to Human)'
    }
}

interface SectionContig {
    title: string,
    description?: string,
    tags?: string[]
}

export const INFO_FIELD_GROUPS: {
    predictedFunction: SectionContig,
    regulatory: SectionContig,
    comparative: SectionContig,
    phenotypicData: SectionContig
} = {
    predictedFunction : {
        title: 'Predicted Function',
        description: 'Annotations related to prediction of functional impact or coding potential.',
        tags: ['CADD_PH', 'SD', 'SM', 'SX', 'NE', 'NF', 'NG', 'NH', 'NK', 'NL', 'NMD', 'NC', 'NJ', 'LOF', 'FC', 'FE']
    },
    regulatory: {
        title: 'Regulatory Data',
                description: 'Annotations related to overlap with known or predicted regulatory elements.',
                tags: ['ENC', 'ENN', 'ENCDNA_SC', 'ENCTFBS_SC', 'ENCTFBS_TF', 'ENCSEG_NM', 'RDB_WS', 'RDB_MF', 'FS_EN', 'FS_WS', 'FS_NS', 'FS_SN', 'FS_TG', 'FS_US', 'FS_SC']
    },
    comparative: {
        title: 'Comparative/Evolutionary Genomics',
                description: '',
                tags: ['PC_VB', 'PC_PL', 'PC_PR', 'PP_VB', 'PP_PL', 'PP_PR']
    },
    phenotypicData: {
        title: 'Phenotypic Data',
                tags: ['CLN_SIG', 'CLN_DN', 'CLN_DNINCL', 'CLN_ALLELE', 'CLN_ALLELEID', 'CLN_VI', 'CLN_DBVARID', 'CLN_GENEINFO', 'CLN_RS', 'OMIMN', 'OMIMT', 'OMIMD', 'OMIMS', 'OMIMM', 'OMIMC', 'OMIMMUS', 'GRASP_AN','GRASP_PH','GRASP_P','GRASP_PL','GRASP_PMID','GRASP_RS', 'CLN']
    }
}

export const IGNORED_INFO_FIELDS = ['SF', 'NA', 'SOR', 'CADD_RS', 'ENCDNA_CT', 'ERBCTA_CT', 'ENCDNA_SC', 'ERBCTA_SC', 'NM', 'CCDS', 'OREGANNO_PMID', 'OREGANNO_TYPE', 'RSID', 'SCSNV_ADA', 'SCSNV_RS', 'SP_SC', 'ENCTFBS_CL', 'ERBSEG_CT', 'ERBSEG_NM', 'ERBSEG_SC', 'ERBSUM_NM', 'ERBSUM_SC', 'ERBTFBS_PB', 'ERBTFBS_TF', 'CLN_DISDB', 'CLN_DISDBINCL', 'CLN_HGVS', 'CLN_REVSTAT', 'CLN_SIGINCL', 'CLN_VC', 'CLN_VCSO', 'CLN_MC', 'CLN_ORIGIN', 'CLN_SSR', 'genotypes']
