import Link from "next/link";
import { prisma } from "@/lib/prisma";
import { normalizeDrops, oddsFor } from "@/lib/cases";
import BuyCaseButton from "./BuyCaseButton";
export const dynamic="force-dynamic";
export default async function CasesPage(){
 let cases:Awaited<ReturnType<typeof prisma.caseDefinition.findMany>>=[];
 try{cases=await prisma.caseDefinition.findMany({where:{active:true},orderBy:{createdAt:"asc"}});}catch{}
 return <div className="page"><div className="eyebrow">COSMETIC STORE / CASES</div><h1>Кейсы со скинами</h1>
  <p className="page-lead">Шансы выпадения показаны заранее. Предметы косметические и не дают преимущества. Покупки выключены до настройки платёжного провайдера.</p>
  {cases.length===0?<section className="panel"><h2>Каталог подготавливается</h2><p className="muted">Настрой базу данных и выполни seed-команду из README.</p></section>:
   <div className="card-grid">{cases.map(c=>{const drops=oddsFor(normalizeDrops(c.drops));return <article className="case-card" key={c.id}>
    <div className="case-art">◇</div><div className="eyebrow" style={{marginTop:18}}>PROJECT VISUAL DROP</div><h2>{c.title}</h2><p className="muted">{c.description}</p>
    <div className="odds">{drops.map(d=><div className="odds-row" key={d.skinId}><span>{d.skinId}</span><b>{d.chance.toFixed(1)}%</b></div>)}</div>
    <div style={{marginTop:18}}><BuyCaseButton caseId={c.id} priceCents={c.priceCents}/></div>
   </article>;})}</div>}
  <p className="muted">Нет аккаунта? <Link href="/register">Зарегистрируйся</Link>. Вывод предметов за деньги не предусмотрен.</p>
 </div>;
}
