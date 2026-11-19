import { PrismaClient } from "@prisma/client";
const prisma=new PrismaClient();
async function main(){
 const price=Math.max(100,Number(process.env.PROJECT_VISUAL_CASE_PRICE_CENTS??299));
 await prisma.caseDefinition.upsert({
  where:{id:"visual-starter"},
  update:{priceCents:price},
  create:{id:"visual-starter",title:"Visual Starter Case",
   description:"Cosmetic-only items. Drop chances are listed before purchase.",
   priceCents:price,currency:"usd",
   drops:[
    {skinId:"Pencil Sword",weight:30},{skinId:"Dual Blades",weight:22},
    {skinId:"Flying Spellbook",weight:18},{skinId:"Akashi Seijuro",weight:12},
    {skinId:"Guts",weight:8},{skinId:"Divine Rapier",weight:6},
    {skinId:"Invoker Skin",weight:3},{skinId:"Queen Skin",weight:1}
   ]}
 });
}
main().finally(()=>prisma.$disconnect());
