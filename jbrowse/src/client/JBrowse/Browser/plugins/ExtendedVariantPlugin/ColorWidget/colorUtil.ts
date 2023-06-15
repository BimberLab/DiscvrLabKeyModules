import {rgb} from "color-convert"
import { colorSchemes } from "./colorSchemes"

export function hexToRGB(hexStr){
// takes a hex string and returns rgb decimals in a list
   let hex = hexStr.replace(/#/g, '')
   if(hex.length !== 6){
      console.error("Six-digit hex code required.")
      return
   }
   const hexList = hex.match(/.{1,2}/g)
   return [parseInt(hexList[0], 16), parseInt(hexList[1], 16), parseInt(hexList[2], 16)]
}

export function generateGradient(hex1, hex2, steps, maxVal){
   let rgb1 = hexToRGB(hex1)
   let rgb2 = hexToRGB(hex2)
   let gradient = []
   gradient.push({
      percent: 0,
      ub: 0,
      r: rgb1[0],
      g: rgb1[1],
      b: rgb1[2],
      hex: rgb.hex(rgb1[0], rgb1[1], rgb1[2]),
   })
   for (let i = 1; i <= (steps); i++){
      let percent = (1/steps) * i
      let gradientStep = {}
      let ub = percent * maxVal
      gradientStep["percent"] = percent
      gradientStep["ub"] = ub
      gradientStep["r"] = rgb1[0] - ((rgb1[0] - rgb2[0]) * percent)
      gradientStep["g"] = rgb1[1] - ((rgb1[1] - rgb2[1]) * percent)
      gradientStep["b"] = rgb1[2] - ((rgb1[2] - rgb2[2]) * percent)
      gradientStep["hex"] = rgb.hex(gradientStep["r"], gradientStep["g"], gradientStep["b"])
      gradient.push(gradientStep)
   }
   return gradient
}

export function generateNumJexl(scheme){
   // scheme: an object from colorSchemes.ts
   // creates a jexl string that returns colors in a gradient based on schema passed
   let jexl = "jexl"
   let gradientSteps = generateGradient(scheme.options["minVal"], scheme.options["maxVal"], scheme.gradientSteps, scheme.maxVal)
   for (let i = 0; i < gradientSteps.length; i++){
      let step = gradientSteps[i]
      if(step === gradientSteps[0]){
         // first entry
         jexl = jexl + ":(" + scheme.jexlComponent + ">=" + (step.percent * scheme.maxVal) + " && "
         step = gradientSteps[i+1]
         jexl = jexl + scheme.jexlComponent + "<=" + (step.percent * scheme.maxVal) + ")?'#" + rgb.hex(step["r"], step["g"], step["b"]) + "'"
      } else if (step === gradientSteps[gradientSteps.length-1]){
         // last entry
         jexl = jexl + ":" + scheme.jexlComponent + "==" + (step.percent * scheme.maxVal) + "?'#" + rgb.hex(step["r"], step["g"], step["b"]) + "'"
      } else {
         jexl = jexl + ":(" + scheme.jexlComponent + ">" + (step.percent * scheme.maxVal) + " && "
         step = gradientSteps[i+1]
         jexl = jexl + scheme.jexlComponent + "<=" + (step.percent * scheme.maxVal) + ")?'#" + rgb.hex(step["r"], step["g"], step["b"]) + "'"
      }
   }
   jexl = jexl + ":'gray'"
   return jexl
}

export function generateOptJexl(scheme){
   // scheme: an object from colorSchemes.ts
   // creates a jexl string that returns colors based on scheme passed
   let jexl = "jexl"
   Object.entries(scheme.options).map(([key, val]) =>
      jexl = jexl + ":" + scheme.jexlComponent + "=='" + key + "'?'" + val + "'"
   )
   jexl = jexl + ":'gray'"

   return jexl
}

export function generateSchemeJexl(schemeName){
   const scheme = colorSchemes[schemeName]
   if (!scheme) {
      console.error("Unknown color scheme: " + schemeName)
      return null
   }

   if (scheme.dataType === "option"){
      return generateOptJexl(scheme)
   } else if (scheme.dataType === "number"){
      return generateNumJexl(scheme)
   }

   console.error("Unknown dataType: " + scheme.dataType)

   return null
}