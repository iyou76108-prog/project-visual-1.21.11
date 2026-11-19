import {NextResponse} from "next/server";
import {prisma} from "@/lib/prisma";
import {normalizeDrops,oddsFor} from "@/lib/cases";
export async function GET(){
 try{
  const rows=await prisma.caseDefinition.findMany({where:{active:true},orderBy:{createdAt:"asc"}});
  return NextResponse.json({cases:rows.map(c=>({id:c.id,title:c.title,description:c.description,priceCents:c.priceCents,currency:c.currency,drops:oddsFor(normalizeDrops(c.drops))}))});
 }catch{return NextResponse.json({cases:[],error:"Catalog database is not initialized."},{status:503});}
}
