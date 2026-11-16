import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
export async function POST(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const now=new Date();
 await prisma.presence.upsert({where:{userId:u.id},create:{userId:u.id,lastSeen:now,expiresAt:new Date(now.getTime()+30000)},update:{lastSeen:now,expiresAt:new Date(now.getTime()+30000)}});
 return NextResponse.json({ok:true,expiresInSeconds:30});
}
export async function GET(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const p=await prisma.presence.findUnique({where:{userId:u.id}});
 return NextResponse.json({online:!!p&&p.expiresAt>new Date(),lastSeen:p?.lastSeen??null});
}
