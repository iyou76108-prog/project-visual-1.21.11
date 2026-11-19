import Link from "next/link";
import { redirect } from "next/navigation";
import { getBrowserUser } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import PairMinecraftButton from "./PairMinecraftButton";
import EquipSkinButton from "./EquipSkinButton";
import OpenCaseButton from "./OpenCaseButton";
export const dynamic="force-dynamic";
export default async function DashboardPage(){
 const user=await getBrowserUser();if(!user)redirect("/login");
 const [credits,inventory,friendCount]=await Promise.all([
  prisma.caseCredit.count({where:{userId:user.id}}),
  prisma.inventoryItem.findMany({where:{userId:user.id},orderBy:{createdAt:"desc"},take:8}),
  prisma.friendship.count({where:{status:"ACCEPTED",OR:[{requesterId:user.id},{addresseeId:user.id}]}})
 ]);
 return <div className="page"><div className="eyebrow">PLAYER ACCOUNT / OVERVIEW</div><h1>Привет, {user.username}</h1>
  <p className="page-lead">Аккаунт подтверждён. Это центр управления твоим PROJECT VISUAL.</p>
  <p className="muted">Сейчас экипирован скин: <b>{user.equippedSkin??"ничего"}</b></p>
  <div className="dashboard-grid">
   <section className="panel"><h2>Связать Minecraft</h2><p className="muted">Код связывает клиент с этим аккаунтом. Пароль от сайта не нужно вводить в игре.</p><PairMinecraftButton/></section>
   <section className="panel"><h2>Твой профиль</h2><div className="stat">EMAIL VERIFIED</div><p>{user.email}</p><div className="stat">{friendCount} FRIEND CONNECTIONS</div><p>Друзей: {friendCount}</p><div className="stat">{credits} CASE CREDITS</div><p>Доступно кейсов: {credits}</p></section>
  </div>
  <section className="panel" style={{marginTop:16}}><h2>Инвентарь скинов</h2>
   {inventory.length===0?<p className="muted">Пока пусто. Кейсы можно посмотреть в магазине.</p>:
    <div className="card-grid">{inventory.map(i=><div className="case-card" key={i.id}><div className="price">COSMETIC</div><h2>{i.skinId}</h2><p className="muted">Получен: {i.createdAt.toLocaleDateString("ru-RU")}</p><EquipSkinButton skinId={i.skinId} active={user.equippedSkin===i.skinId}/></div>)}</div>}
   {credits>0&&<OpenCaseButton caseId="visual-starter" credits={credits}/>}
   <Link className="button ghost" href="/cases">Открыть магазин кейсов</Link>
  </section></div>;
}
