import {NextResponse} from "next/server";
import Stripe from "stripe";
import {getStripe} from "@/lib/stripe";
import {prisma} from "@/lib/prisma";
export async function POST(request:Request){
 const signature=request.headers.get("stripe-signature"),secret=process.env.STRIPE_WEBHOOK_SECRET;
 if(!signature||!secret)return NextResponse.json({error:"Webhook is not configured."},{status:503});
 let event:Stripe.Event;
 try{event=getStripe().webhooks.constructEvent(await request.text(),signature,secret);}
 catch{return NextResponse.json({error:"Invalid signature."},{status:400});}
 if(event.type==="checkout.session.completed"){
  const session=event.data.object as Stripe.Checkout.Session,id=session.metadata?.purchaseId;
  if(id&&session.payment_status==="paid"){
   await prisma.$transaction(async tx=>{
    const p=await tx.casePurchase.findUnique({where:{id}});
    if(!p||p.status==="PAID")return;
    await tx.casePurchase.update({where:{id},data:{status:"PAID",paidAt:new Date()}});
    await tx.caseCredit.create({data:{userId:p.userId,caseId:p.caseId}});
   });
  }
 }
 return NextResponse.json({received:true});
}
