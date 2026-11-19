"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function LoginPage() {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const router = useRouter();

  useEffect(() => {
    setVerified(new URLSearchParams(window.location.search).get("verified") === "1");
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    const form = new FormData(event.currentTarget);
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: form.get("email"), password: form.get("password") })
    });
    const result = await response.json();
    if (response.ok) {
      router.push("/dashboard");
      router.refresh();
    } else {
      setMessage(result.error ?? "Не удалось войти.");
    }
    setBusy(false);
  }

  return <div className="page">
    <div className="eyebrow">ACCOUNT / SIGN IN</div>
    <h1>С возвращением</h1>
    {verified && <p className="form-message">Почта подтверждена. Теперь можно войти.</p>}
    <form className="form-card" onSubmit={submit}>
      <div className="field"><label>Почта</label><input name="email" type="email" required autoComplete="email"/></div>
      <div className="field"><label>Пароль</label><input name="password" type="password" required autoComplete="current-password"/></div>
      <button className="button primary" disabled={busy}>{busy ? "Входим..." : "Войти"}</button>
      {message && <p className="form-message">{message}</p>}
      <p className="link-row">Нет аккаунта? <Link href="/register">Зарегистрироваться</Link></p>
    </form>
  </div>;
}
