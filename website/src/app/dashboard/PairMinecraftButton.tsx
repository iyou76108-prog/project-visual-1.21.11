"use client";
import { useState } from "react";
export default function PairMinecraftButton(){
 const [code,setCode]=useState("");const [message,setMessage]=useState("");const [busy,setBusy]=useState(false);
 async function createCode(){
  setBusy(true);setMessage("");const r=await fetch("/api/minecraft/pair-code",{method:"POST"});const d=await r.json();
  if(r.ok)setCode(d.code);else setMessage(d.error??"Не удалось создать код.");setBusy(false);
 }
 return <div><button className="button primary" disabled={busy} onClick={createCode}>{busy?"Создаём...":"Создать код для Minecraft"}</button>
  {code&&<div className="pair-code">{code}</div>}
  {code&&<p className="muted">В игре выполни <b>/pv login</b> и введи этот одноразовый код. Он действует 5 минут.</p>}
  {message&&<p className="form-message">{message}</p>}</div>;
}
