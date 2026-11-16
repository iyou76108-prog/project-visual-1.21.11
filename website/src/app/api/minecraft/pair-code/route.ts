import {NextResponse} from "next/server";
import {getBrowserUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
import {hashSecret,randomSecret} from "@/lib/security";
export async function POST(){
 const u=await getBrowserUser();if(!u)return NextResponse.json({error:"Войди в аккаунт на сайте."},{status:401});
 const code="PV-"+randomSecret(6).replace(/[-_]/g,"").slice(0,8).toUpperCase();
 await prisma.minecraftPairCode.create({data:{userId:u.id,codeHash:hashSecret(code),expiresAt:new Date(Date.now()+300000)}});
 return NextResponse.json({code,expiresInSeconds:300});
}
