import {NextResponse} from "next/server";
import Stripe from "stripe";
import {getStripe} from "@/lib/stripe";
import {prisma} from "@/lib/prisma";

export async function POST(request:Request){
 const signature=request.headers.get("stripe-signature"),secret=process.env.STRIPE_WEBHOOK_SECRET;
 if(!signature||!secret)return NextResponse.json({error:"Webhook is not configured."},{status:503});
 let event:Stripe.Event;
 try{
  // Stripe requires the untouched request body for signature verification.
  event=getStripe().webhooks.constructEvent(await request.text(),signature,secret);
 }catch{return NextResponse.json({error:"Invalid signature."},{status:400});}

 if(event.type==="checkout.session.completed"||event.type==="checkout.session.async_payment_succeeded"){
  const session=event.data.object as Stripe.Checkout.Session;
  const purchaseId=session.metadata?.purchaseId;
  if(purchaseId&&session.payment_status==="paid"){
   await prisma.$transaction(async tx=>{
    const purchase=await tx.casePurchase.findUnique({where:{id:purchaseId}});
    if(!purchase||purchase.status!=="PENDING"||purchase.providerSessionId!==session.id)return;
    if(session.amount_total!==purchase.amountCents
       ||(session.currency??"").toLowerCase()!==purchase.currency.toLowerCase())return;
    // Compare-and-set makes duplicate or simultaneous webhook delivery idempotent.
    const claimed=await tx.casePurchase.updateMany({
     where:{id:purchase.id,status:"PENDING",providerSessionId:session.id},
     data:{status:"PAID",paidAt:new Date()}
    });
    if(claimed.count===1){
     await tx.caseCredit.create({data:{userId:purchase.userId,caseId:purchase.caseId}});
    }
   });
  }
 }
 return NextResponse.json({received:true});
}
