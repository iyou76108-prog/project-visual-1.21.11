import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { hashSecret, randomSecret } from "@/lib/security";
const COOKIE="pv_session";
const DAYS=Math.min(90,Math.max(1,Number(process.env.SESSION_DAYS??30)));
export async function getBrowserUser(){
 const jar=await cookies();const raw=jar.get(COOKIE)?.value;if(!raw)return null;
 const session=await prisma.session.findUnique({where:{tokenHash:hashSecret(raw)},include:{user:true}});
 if(!session||session.expiresAt<=new Date()||!session.user.emailVerifiedAt){
  if(session)await prisma.session.delete({where:{id:session.id}}).catch(()=>undefined);
  return null;
 }
 return session.user;
}
export async function setBrowserSession(response:NextResponse,userId:string){
 const raw=randomSecret(36);const expiresAt=new Date(Date.now()+DAYS*86400000);
 await prisma.session.create({data:{userId,tokenHash:hashSecret(raw),expiresAt}});
 response.cookies.set(COOKIE,raw,{httpOnly:true,secure:process.env.NODE_ENV==="production",sameSite:"lax",path:"/",expires:expiresAt});
}
export async function clearBrowserSession(request:NextRequest,response:NextResponse){
 const raw=request.cookies.get(COOKIE)?.value;
 if(raw)await prisma.session.deleteMany({where:{tokenHash:hashSecret(raw)}});
 response.cookies.set(COOKIE,"",{httpOnly:true,secure:process.env.NODE_ENV==="production",sameSite:"lax",path:"/",maxAge:0});
}
export async function getMinecraftUser(request:Request){
 const match=(request.headers.get("authorization")??"").match(/^Bearer\s+(.+)$/i);
 if(!match)return null;
 const token=await prisma.minecraftToken.findUnique({where:{tokenHash:hashSecret(match[1])},include:{user:true}});
 if(!token||token.expiresAt<=new Date()||!token.user.emailVerifiedAt)return null;
 await prisma.minecraftToken.update({where:{id:token.id},data:{lastUsedAt:new Date()}});
 return token.user;
}
