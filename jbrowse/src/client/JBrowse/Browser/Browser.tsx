import React, {useState, useEffect} from 'react'
//import 'fontsource-roboto'
import {
  createViewState,
  createJBrowseTheme,
  JBrowseLinearGenomeView,
  loadPlugins,
  ThemeProvider,
} from '@jbrowse/react-linear-genome-view'
import { PluginConstructor } from '@jbrowse/core/Plugin'
import { Ajax, Utils, ActionURL } from '@labkey/api'

const theme = createJBrowseTheme()
/*
export const exampleAssembly = {
  name: 'volvox',
  aliases: ['vvx'],
  sequence: {
    type: 'ReferenceSequenceTrack',
    trackId: 'volvox_refseq',
    adapter: {
      type: 'TwoBitAdapter',
      twoBitLocation: {
        uri: 'public/volvox.2bit',
      },
    },
    rendering: {
      type: 'DivSequenceRenderer',
    },
  },
  refNameAliases: {
    adapter: {
      type: 'FromConfigAdapter',
      features: [
        {
          refName: 'ctgA',
          uniqueId: 'alias1',
          aliases: ['A', 'contigA'],
        },
        {
          refName: 'ctgB',
          uniqueId: 'alias2',
          aliases: ['B', 'contigB'],
        },
      ],
    },
  },
}

export const exampleTracks = [
  {
    type: 'VariantTrack',
    trackId: 'volvox_test_vcf',
    name: 'volvox 1000genomes vcf',
    assemblyNames: ['volvox'],
    category: ['VCF'],
    adapter: {
      type: 'VcfTabixAdapter',
      vcfGzLocation: {
        uri: 'public/volvox.test.vcf.gz'
       // uri: 'public/volvox.test.vcf.gz',
      },
      index: {
        location: {
          uri: 'public/volvox.test.vcf.gz.tbi',
          //uri: 'public/volvox.test.vcf.gz.tbi',
        },
      },
    },
  },
  {
    type: 'VariantTrack',
    trackId: 'volvox_filtered_vcf',
    name: 'volvox filtered vcf',
    assemblyNames: ['volvox'],
    category: ['VCF'],
    adapter: {
      type: 'VcfTabixAdapter',
      vcfGzLocation: {
        uri: 'public/volvox.filtered.vcf.gz',
      },
      index: {
        location: {
          uri: 'public/volvox.filtered.vcf.gz.tbi',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
        maxFeatureGlyphExpansion: 0,
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox-long-reads-sv-bam',
    name: 'volvox-long reads with SV',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'BamAdapter',
      bamLocation: {
        uri: 'public/volvox-long-reads-sv.bam',
      },
      index: {
        location: {
          uri: 'public/volvox-long-reads-sv.bam.bai',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox-long-reads-sv-cram',
    name: 'volvox-long reads with SV (cram)',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'CramAdapter',
      cramLocation: {
        uri: 'public/volvox-long-reads-sv.cram',
      },
      craiLocation: {
        uri: 'public/volvox-long-reads-sv.cram.crai',
      },
      sequenceAdapter: {
        type: 'TwoBitAdapter',
        twoBitLocation: {
          uri: 'public/volvox.2bit',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox-long-reads-cram',
    name: 'volvox-long reads (cram)',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'CramAdapter',
      cramLocation: {
        uri: 'public/volvox-long-reads.fastq.sorted.cram',
      },
      craiLocation: {
        uri: 'public/volvox-long-reads.fastq.sorted.cram.crai',
      },
      sequenceAdapter: {
        type: 'TwoBitAdapter',
        twoBitLocation: {
          uri: 'public/volvox.2bit',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox-long-reads-bam',
    name: 'volvox-long reads',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'BamAdapter',
      bamLocation: {
        uri: 'public/volvox-long-reads.fastq.sorted.bam',
      },
      index: {
        location: {
          uri: 'public/volvox-long-reads.fastq.sorted.bam.bai',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_samspec_cram',
    name: 'volvox-samspec (cram)',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'CramAdapter',
      cramLocation: {
        uri: 'public/volvox-samspec.cram',
      },
      craiLocation: {
        uri: 'public/volvox-samspec.cram.crai',
      },
      sequenceAdapter: {
        type: 'TwoBitAdapter',
        twoBitLocation: {
          uri: 'public/volvox.2bit',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_samspec',
    name: 'volvox-samspec',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'BamAdapter',
      bamLocation: {
        uri: 'public/volvox-samspec.bam',
      },
      index: {
        location: {
          uri: 'public/volvox-samspec.bam.bai',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_sv_cram',
    name: 'volvox-sv (cram)',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'CramAdapter',
      cramLocation: {
        uri: 'public/volvox-sv.cram',
      },
      craiLocation: {
        uri: 'public/volvox-sv.cram.crai',
      },
      sequenceAdapter: {
        type: 'TwoBitAdapter',
        twoBitLocation: {
          uri: 'public/volvox.2bit',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_sv',
    name: 'volvox-sv',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'BamAdapter',
      bamLocation: {
        uri: 'public/volvox-sv.bam',
      },
      index: {
        location: {
          uri: 'public/volvox-sv.bam.bai',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'BasicTrack',
    trackId: 'gff3tabix_genes',
    assemblyNames: ['volvox'],
    name: 'GFF3Tabix genes',
    category: ['Miscellaneous'],
    adapter: {
      type: 'Gff3TabixAdapter',
      gffGzLocation: {
        uri: 'public/volvox.sort.gff3.gz',
      },
      index: {
        location: {
          uri: 'public/volvox.sort.gff3.gz.tbi',
        },
      },
    },
    renderer: {
      type: 'SvgFeatureRenderer',
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_cram',
    name: 'volvox-sorted.cram',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'CramAdapter',
      cramLocation: {
        uri: 'public/volvox-sorted.cram',
      },
      craiLocation: {
        uri: 'public/volvox-sorted.cram.crai',
      },
      sequenceAdapter: {
        type: 'TwoBitAdapter',
        twoBitLocation: {
          uri: 'public/volvox.2bit',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'AlignmentsTrack',
    trackId: 'volvox_bam',
    name: 'volvox-sorted.bam',
    assemblyNames: ['volvox'],
    category: ['Alignments'],
    adapter: {
      type: 'BamAdapter',
      bamLocation: {
        uri: 'public/volvox-sorted.bam',
      },
      index: {
        location: {
          uri: 'public/volvox-sorted.bam.bai',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
      SNPCoverageRenderer: {
        type: 'SNPCoverageRenderer',
      },
    },
  },
  {
    type: 'VariantTrack',
    trackId: 'TBggZ1Rwy_p',
    name: 'volvox filtered vcf',
    assemblyNames: ['volvox'],
    category: ['Variants'],
    adapter: {
      type: 'VcfTabixAdapter',
      vcfGzLocation: {
        uri: 'public/volvox.filtered.vcf.gz',
      },
      index: {
        location: {
          uri: 'public/volvox.filtered.vcf.gz.tbi',
        },
      },
    },
    renderers: {
      PileupRenderer: {
        type: 'PileupRenderer',
      },
      SvgFeatureRenderer: {
        type: 'SvgFeatureRenderer',
      },
    },
  },
  {
    type: 'BasicTrack',
    trackId: 'bigbed_genes',
    name: 'BigBed genes',
    assemblyNames: ['volvox'],
    category: ['Miscellaneous'],
    adapter: {
      type: 'BigBedAdapter',
      bigBedLocation: {
        uri: 'public/volvox.bb',
      },
    },
    renderer: {
      type: 'SvgFeatureRenderer',
    },
  },
  {
    type: 'BasicTrack',
    trackId: 'bedtabix_genes',
    name: 'BedTabix genes',
    assemblyNames: ['volvox'],
    category: ['Miscellaneous'],
    adapter: {
      type: 'BedTabixAdapter',
      bedGzLocation: {
        uri: 'public/volvox-bed12.bed.gz',
      },
      index: {
        type: 'TBI',
        location: {
          uri: 'public/volvox-bed12.bed.gz.tbi',
        },
      },
    },
    renderer: {
      type: 'SvgFeatureRenderer',
    },
  },
]

export const exampleSession = {
  name: 'this session',
  view: {
    id: 'linearGenomeView',
    type: 'LinearGenomeView',
    displayName: 'myView',
    bpPerPx: 0.5,
    displayedRegions: [
      {
        refName: 'ctgA',
        start: 0,
        end: 50001,
        reversed: false,
        assemblyName: 'volvox',
      },
    ],
    tracks: [
      {
        type: 'ReferenceSequenceTrack',
        height: 100,
        configuration: 'volvox_refseq',
      },
    ],
  },
}
*/
function generateViewState(genome, plugins){
  console.log("plugins in generateViewState: ", plugins)
  return createViewState({
      assembly: genome.assembly ?? genome.assemblies,
      tracks: genome.tracks,
      configuration: genome.configuration,
      plugins: plugins,
      location: genome.location,
      defaultSession: genome.defaultSession,
      onChange: genome.onChange
  })
}

function View(){
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session')

    const [state, setState] = useState(null);
    const [plugins, setPlugins] = useState<PluginConstructor[]>();

    useEffect(() => {
        Ajax.request({
            url: ActionURL.buildURL('jbrowse', 'getSession.api'),
            method: 'GET',
            success: async function(res){
                let jsonRes = JSON.parse(res.response);
                if (jsonRes.plugins != null){
                    var loadedPlugins = null
                    try {
                        loadedPlugins = await loadPlugins(jsonRes.plugins);
                    } catch (error) {
                        console.error("Error: ", error)
                    }
                    setPlugins(loadedPlugins);
                }
                setState(generateViewState(jsonRes, loadedPlugins));
            },
            failure: function(res){
                setState("invalid");
                console.log(res);
            },
            params: {session: session}
        });
    }, []);

    if(session === null){
        return(<p>Error - no session provided.</p>)
    }
    else if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }
    return (
      <ThemeProvider theme={theme}>
          <JBrowseLinearGenomeView viewState={state} />
      </ThemeProvider>
    )
}

export default View

