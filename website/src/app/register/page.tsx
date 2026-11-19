"use client";
import Link from "next/link";
import { useState } from "react";
export default function RegisterPage(){
 const [message,setMessage]=useState("");const [busy,setBusy]=useState(false);
 async function submit(e:React.FormEvent<HTMLFormElement>){
  e.preventDefault();setBusy(true);setMessage("");const f=new FormData(e.currentTarget);
  const r=await fetch("/api/auth/register",{method:"POST",headers:{"Content-Type":"application/json"},
   body:JSON.stringify({email:f.get("email"),username:f.get("username"),password:f.get("password")})});
  const d=await r.json();setMessage(d.message??d.error??"Не удалось создать аккаунт.");setBusy(false);
 }
 return <div className="page"><div className="eyebrow">ACCOUNT / CREATE</div><h1>Создать аккаунт</h1><p className="page-lead">После регистрации мы отправим письмо. Подтверди почту перед входом в Minecraft-клиент.</p>
  <form className="form-card" onSubmit={submit}>
   <div className="field"><label>Имя пользователя</label><input name="username" minLength={3} maxLength={16} pattern="[A-Za-z0-9_]+" required placeholder="VisualPlayer"/></div>
   <div className="field"><label>Электронная почта</label><input name="email" type="email" required placeholder="you@example.com"/></div>
   <div className="field"><label>Пароль (не менее 10 символов)</label><input name="password" type="password" minLength={10} required autoComplete="new-password"/></div>
   <button className="button primary" disabled={busy}>{busy?"Создаём...":"Зарегистрироваться"}</button>
   {message&&<p className="form-message">{message}</p>}<p className="link-row">Уже есть аккаунт? <Link href="/login">Войти</Link></p>
  </form></div>;
}
