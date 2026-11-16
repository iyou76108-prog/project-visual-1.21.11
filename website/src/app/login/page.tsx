"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
export default function LoginPage(){
 const [message,setMessage]=useState("");const [busy,setBusy]=useState(false);const router=useRouter();const params=useSearchParams();
 async function submit(e:React.FormEvent<HTMLFormElement>){
  e.preventDefault();setBusy(true);setMessage("");const f=new FormData(e.currentTarget);
  const r=await fetch("/api/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},
   body:JSON.stringify({email:f.get("email"),password:f.get("password")})});
  const d=await r.json();if(r.ok){router.push("/dashboard");router.refresh();}else setMessage(d.error??"Не удалось войти.");setBusy(false);
 }
 return <div className="page"><div className="eyebrow">ACCOUNT / SIGN IN</div><h1>С возвращением</h1>
  {params.get("verified")==="1"&&<p className="form-message">Почта подтверждена. Теперь можно войти.</p>}
  <form className="form-card" onSubmit={submit}>
   <div className="field"><label>Почта</label><input name="email" type="email" required autoComplete="email"/></div>
   <div className="field"><label>Пароль</label><input name="password" type="password" required autoComplete="current-password"/></div>
   <button className="button primary" disabled={busy}>{busy?"Входим...":"Войти"}</button>
   {message&&<p className="form-message">{message}</p>}<p className="link-row">Нет аккаунта? <Link href="/register">Зарегистрироваться</Link></p>
  </form></div>;
}
