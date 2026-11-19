export type CaseDrop={skinId:string;weight:number};
export function normalizeDrops(value:unknown):CaseDrop[]{
 if(!Array.isArray(value))return [];
 return value.filter((x):x is CaseDrop=>!!x&&typeof x==="object"&&typeof (x as CaseDrop).skinId==="string"&&Number.isFinite((x as CaseDrop).weight)&&(x as CaseDrop).weight>0);
}
export function oddsFor(drops:CaseDrop[]){
 const total=drops.reduce((n,d)=>n+d.weight,0);
 return drops.map(d=>({...d,chance:total>0?d.weight*100/total:0}));
}
export function chooseDrop(drops:CaseDrop[]){
 const total=drops.reduce((n,d)=>n+d.weight,0);
 if(total<=0)throw new Error("Case has no valid drops.");
 let roll=Math.random()*total;
 for(const d of drops){roll-=d.weight;if(roll<0)return d.skinId;}
 return drops[drops.length-1].skinId;
}
