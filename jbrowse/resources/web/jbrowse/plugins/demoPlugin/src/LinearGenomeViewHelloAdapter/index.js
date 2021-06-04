import {
    ConfigurationSchema,
    readConfObject,
} from "@jbrowse/core/configuration";
import { ObservableCreate } from "@jbrowse/core/util/rxjs";
import { BaseFeatureDataAdapter } from "@jbrowse/core/data_adapters/BaseAdapter";
import SimpleFeature from "@jbrowse/core/util/simpleFeature";
import stringify from "json-stable-stringify";

export const configSchema = ConfigurationSchema(
  "LGVHelloAdapter",
  {
    base: {
      type: "fileLocation",
      description: "base URL for the LGVHelloWorld",
      defaultValue: {
        uri: ""//"https://api.genome.ucsc.edu",
      },
    },
    track: {
      type: "string",
      description: "the track to select data from",
      defaultValue: "",
    },
  },
  { explicitlyTyped: true },
);

export function helloWorldProcessedTranscript(feature) {
  const newData = {};
  feature.tags().forEach(tag => {
    newData[tag] = feature.get(tag);
  });
  newData["test tag"] = "hello world";
  const newFeature = new SimpleFeature({
    data: newData,
    id: feature.id(),
  });
}


export class AdapterClass extends BaseFeatureDataAdapter {
  contructor(config) {
    //super(config)
    this.config = config
  }

  getFeatures(region){
    const {assemblyName, start, end, refName } = region;
    return ObservableCreate(async observer => {
      const { uri } = readConfObject(this.config, "base");
      const track = readConfObject(this.config, "track");
      try {
        const result = await fetch(
          '${uri}/getData/track?' +
          'genome=${assemblyName};track=${track};' +
          'chrom=${refName};start={$start};end=${end}',
        );
        if(!result.ok){
          throw new Error(
            'Failed to fetch ${result.status} ${result.statusText}'
          );
        }
        const data = await result.json();
        //...
        data = helloWorldProcessedTranscript(new SimpleFeature(data));
        observer.next(data);
        observer.complete()
      } catch (e) {
        observer.error(e);
      }
    })
  }

  async getRefNames() {
    const arr = [];
    for (let i = 0; i < 23; i++) {
      arr.push(`chr${i}`);
    }
    return arr;
  }

  freeResources() {}
}