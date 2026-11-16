import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
export async function GET(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const rows=await prisma.friendship.findMany({where:{status:"ACCEPTED",OR:[{requesterId:u.id},{addresseeId:u.id}]},include:{
  requester:{select:{id:true,username:true,equippedSkin:true}},addressee:{select:{id:true,username:true,equippedSkin:true}}
 }});
 const ids=rows.map(r=>r.requesterId===u.id?r.addresseeId:r.requesterId);
 const active=await prisma.presence.findMany({where:{userId:{in:ids},expiresAt:{gt:new Date()}},select:{userId:true}});
 const online=new Set(active.map(p=>p.userId));
 return NextResponse.json({players:rows.map(r=>{const p=r.requesterId===u.id?r.addressee:r.requester;return {username:p.username,equippedSkin:p.equippedSkin,online:online.has(p.id)};})});
}
