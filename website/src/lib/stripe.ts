import Stripe from "stripe";
let instance:Stripe|null=null;
export function getStripe(){
 const key=process.env.STRIPE_SECRET_KEY;
 if(!key)throw new Error("Payments are not enabled yet.");
 if(!instance)instance=new Stripe(key);
 return instance;
}
