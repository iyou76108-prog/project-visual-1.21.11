import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
export async function GET(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const rows=await prisma.friendship.findMany({where:{OR:[{requesterId:u.id},{addresseeId:u.id}]},include:{
  requester:{select:{id:true,username:true,equippedSkin:true}},addressee:{select:{id:true,username:true,equippedSkin:true}}
 },orderBy:{updatedAt:"desc"}});
 const ids=rows.map(r=>r.requesterId===u.id?r.addresseeId:r.requesterId);
 const active=await prisma.presence.findMany({where:{userId:{in:ids},expiresAt:{gt:new Date()}},select:{userId:true}});
 const online=new Set(active.map(p=>p.userId));
 return NextResponse.json({friends:rows.map(r=>{
  const other=r.requesterId===u.id?r.addressee:r.requester;
  return {id:r.id,username:other.username,status:r.status,incoming:r.addresseeId===u.id,online:online.has(other.id),equippedSkin:other.equippedSkin};
 })});
}
export async function POST(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const body=await request.json().catch(()=>null) as {username?:string}|null;
 const username=(body?.username??"").trim();
 if(!/^[A-Za-z0-9_]{3,16}$/.test(username))return NextResponse.json({error:"Укажи ник PROJECT VISUAL."},{status:400});
 const other=await prisma.user.findUnique({where:{username}});
 if(!other||other.id===u.id)return NextResponse.json({error:"Игрок не найден."},{status:404});
 const prior=await prisma.friendship.findFirst({where:{OR:[{requesterId:u.id,addresseeId:other.id},{requesterId:other.id,addresseeId:u.id}]}});
 if(prior)return NextResponse.json({error:prior.status==="ACCEPTED"?"Вы уже друзья.":"Запрос уже существует."},{status:409});
 const row=await prisma.friendship.create({data:{requesterId:u.id,addresseeId:other.id}});
 return NextResponse.json({ok:true,requestId:row.id},{status:201});
}
export async function PATCH(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const body=await request.json().catch(()=>null) as {requestId?:string;action?:string}|null;
 if(!body?.requestId||!["accept","decline"].includes(body.action??""))return NextResponse.json({error:"Invalid request."},{status:400});
 const row=await prisma.friendship.findFirst({where:{id:body.requestId,addresseeId:u.id,status:"PENDING"}});
 if(!row)return NextResponse.json({error:"Запрос не найден."},{status:404});
 const updated=await prisma.friendship.update({where:{id:row.id},data:{status:body.action==="accept"?"ACCEPTED":"DECLINED"}});
 return NextResponse.json({ok:true,status:updated.status});
}
