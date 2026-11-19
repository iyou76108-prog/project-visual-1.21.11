import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";

export async function GET(request:Request){
 const user=await getMinecraftUser(request);
 if(!user)return NextResponse.json({error:"Not authenticated"},{status:401});
 return NextResponse.json({user:{
  id:user.id,username:user.username,equippedSkin:user.equippedSkin
 }});
}
