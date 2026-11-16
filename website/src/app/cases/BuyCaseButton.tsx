"use client";
import { useState } from "react";
export default function BuyCaseButton({caseId,priceCents}:{caseId:string;priceCents:number}){
 const [busy,setBusy]=useState(false);const [error,setError]=useState("");
 async function buy(){
  setBusy(true);setError("");const r=await fetch("/api/shop/checkout",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({caseId})});const d=await r.json();
  if(r.ok&&d.url)window.location.assign(d.url);else setError(d.error??"Покупки пока не включены.");setBusy(false);
 }
 return <><button className="button primary" onClick={buy} disabled={busy}>{busy?"Открываю checkout":"Купить · $"+(priceCents/100).toFixed(2)}</button>{error&&<p className="form-message">{error}</p>}</>;
}
