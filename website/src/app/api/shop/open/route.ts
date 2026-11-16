import {NextResponse} from "next/server";
import {getBrowserUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
import {chooseDrop,normalizeDrops} from "@/lib/cases";
export async function POST(request:Request){
 const u=await getBrowserUser();if(!u)return NextResponse.json({error:"Войди в аккаунт."},{status:401});
 const body=await request.json().catch(()=>null) as {caseId?:string}|null;
 if(!body?.caseId)return NextResponse.json({error:"Кейс не выбран."},{status:400});
 try{
  const reward=await prisma.$transaction(async tx=>{
   const credit=await tx.caseCredit.findFirst({where:{userId:u.id,caseId:body.caseId},orderBy:{createdAt:"asc"}});
   if(!credit)throw new Error("NO_CASE_CREDIT");
   const definition=await tx.caseDefinition.findUniqueOrThrow({where:{id:credit.caseId}});
   const skinId=chooseDrop(normalizeDrops(definition.drops));
   await tx.caseCredit.delete({where:{id:credit.id}});
   return tx.inventoryItem.create({data:{userId:u.id,skinId,source:"case:"+definition.id}});
  });
  return NextResponse.json({reward:reward.skinId});
 }catch(e){
  if(e instanceof Error&&e.message==="NO_CASE_CREDIT")return NextResponse.json({error:"Нет купленных кейсов для открытия."},{status:409});
  return NextResponse.json({error:"Не удалось открыть кейс."},{status:500});
 }
}
