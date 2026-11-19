import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";

export async function GET(request:Request){
 const user=await getMinecraftUser(request);
 if(!user)return NextResponse.json({error:"Not authenticated"},{status:401});
 const now=new Date();
 const online=await prisma.user.findMany({
  where:{id:{not:user.id},presence:{is:{expiresAt:{gt:now}}}},
  select:{username:true,equippedSkin:true}
 });
 return NextResponse.json({players:online.map(player=>({
  username:player.username,equippedSkin:player.equippedSkin,online:true
 }))});
}
