"use client";
import {useRouter} from "next/navigation";
import {useState} from "react";

export default function OpenCaseButton({caseId,credits}:{caseId:string;credits:number}){
 const [busy,setBusy]=useState(false),[reward,setReward]=useState(""),[error,setError]=useState("");
 const router=useRouter();
 async function openCase(){
  setBusy(true);setError("");setReward("");
  try{
   const response=await fetch("/api/shop/open",{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({caseId})
   });
   const data=await response.json();
   if(!response.ok)throw new Error(data.error??"Не удалось открыть кейс.");
   setReward(data.reward??"Награда получена");router.refresh();
  }catch(e){setError(e instanceof Error?e.message:"Ошибка сети.");}
  finally{setBusy(false);}
 }
 return <section className="panel" style={{margin:"20px 0"}}>
  <h2>Твои кейсы: {credits}</h2>
  <button className="button primary" type="button" onClick={openCase} disabled={busy}>
   {busy?"Открываем...":"Открыть кейс"}
  </button>
  {reward&&<p className="form-message">Выпало: <b>{reward}</b></p>}
  {error&&<p className="form-message">{error}</p>}
 </section>;
}
