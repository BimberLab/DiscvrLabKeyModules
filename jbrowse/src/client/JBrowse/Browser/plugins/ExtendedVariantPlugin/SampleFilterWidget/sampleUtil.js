
export function parseSampleCSV(csv){
   let samples = null
   if(csv){
     samples = csv.replace(/\s/g, "").split(",")
   }
   return samples
}