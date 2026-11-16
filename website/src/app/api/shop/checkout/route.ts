import {NextResponse} from "next/server";
import {getBrowserUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
import {getStripe} from "@/lib/stripe";
export async function POST(request:Request){
 const u=await getBrowserUser();if(!u)return NextResponse.json({error:"Войди и подтверди почту."},{status:401});
 const body=await request.json().catch(()=>null) as {caseId?:string}|null;
 if(!body?.caseId)return NextResponse.json({error:"Не выбран кейс."},{status:400});
 const item=await prisma.caseDefinition.findFirst({where:{id:body.caseId,active:true}});
 if(!item)return NextResponse.json({error:"Кейс не найден."},{status:404});
 let stripe;try{stripe=getStripe();}catch{return NextResponse.json({error:"Платежи пока выключены. Сайт ещё не опубликован."},{status:503});}
 const purchase=await prisma.casePurchase.create({data:{userId:u.id,caseId:item.id,amountCents:item.priceCents,currency:item.currency}});
 try{
  const base=process.env.APP_URL??"http://localhost:3000";
  const session=await stripe.checkout.sessions.create({
   mode:"payment",
   line_items:[{quantity:1,price_data:{currency:item.currency,unit_amount:item.priceCents,product_data:{name:item.title,description:item.description}}}],
   success_url:base+"/dashboard?purchase=success",cancel_url:base+"/cases?purchase=cancelled",
   metadata:{purchaseId:purchase.id,userId:u.id,caseId:item.id}
  });
  await prisma.casePurchase.update({where:{id:purchase.id},data:{providerSessionId:session.id}});
  return NextResponse.json({url:session.url});
 }catch{
  await prisma.casePurchase.update({where:{id:purchase.id},data:{status:"FAILED"}});
  return NextResponse.json({error:"Не удалось создать платёжную сессию."},{status:502});
 }
}
