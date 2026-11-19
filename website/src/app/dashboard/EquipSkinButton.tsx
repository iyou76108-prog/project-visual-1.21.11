"use client";
import {useRouter} from "next/navigation";
import {useState} from "react";

export default function EquipSkinButton({skinId,active}:{skinId:string;active:boolean}){
 const [busy,setBusy]=useState(false),[message,setMessage]=useState("");
 const router=useRouter();
 async function equip(){
  setBusy(true);setMessage("");
  try{
   const response=await fetch("/api/account/equip-skin",{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({skinId})
   });
   const data=await response.json();
   if(!response.ok)throw new Error(data.error??"Не удалось выбрать скин.");
   setMessage("Экипирован");router.refresh();
  }catch(error){setMessage(error instanceof Error?error.message:"Ошибка сети.");}
  finally{setBusy(false);}
 }
 return <div style={{marginTop:12}}>
  <button className="button ghost" type="button" onClick={equip} disabled={busy||active}>
   {active?"Экипирован":busy?"Сохраняю...":"Надеть скин"}
  </button>
  {message&&<p className="form-message">{message}</p>}
 </div>;
}
