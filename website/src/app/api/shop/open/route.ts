import {NextResponse} from "next/server";
import {randomInt} from "node:crypto";
import {getBrowserUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
import {normalizeDrops} from "@/lib/cases";

export async function POST(request:Request){
 const user=await getBrowserUser();
 if(!user)return NextResponse.json({error:"Войди в аккаунт."},{status:401});
 const body=await request.json().catch(()=>null) as {caseId?:string}|null;
 if(!body?.caseId)return NextResponse.json({error:"Кейс не выбран."},{status:400});
 try{
  const reward=await prisma.$transaction(async tx=>{
   const credit=await tx.caseCredit.findFirst({
    where:{userId:user.id,caseId:body.caseId},orderBy:{createdAt:"asc"}
   });
   if(!credit)throw new Error("NO_CASE_CREDIT");
   const definition=await tx.caseDefinition.findUniqueOrThrow({where:{id:credit.caseId}});
   const drops=normalizeDrops(definition.drops);
   if(!drops.length)throw new Error("EMPTY_CASE");
   // Store weights are integers in the seeded catalogue; use a cryptographic RNG.
   const weights=drops.map(drop=>Math.max(1,Math.round(drop.weight*1000)));
   const total=weights.reduce((sum,weight)=>sum+weight,0);
   let roll=randomInt(total),skinId=drops[drops.length-1].skinId;
   for(let i=0;i<drops.length;i++){roll-=weights[i];if(roll<0){skinId=drops[i].skinId;break;}}
   await tx.caseCredit.delete({where:{id:credit.id}});
   return tx.inventoryItem.create({data:{userId:user.id,skinId,source:"case:"+definition.id}});
  });
  return NextResponse.json({reward:reward.skinId});
 }catch(error){
  if(error instanceof Error&&error.message==="NO_CASE_CREDIT")
   return NextResponse.json({error:"Нет купленных кейсов для открытия."},{status:409});
  if(error instanceof Error&&error.message==="EMPTY_CASE")
   return NextResponse.json({error:"У этого кейса пока нет наград."},{status:409});
  return NextResponse.json({error:"Не удалось открыть кейс."},{status:500});
 }
}
