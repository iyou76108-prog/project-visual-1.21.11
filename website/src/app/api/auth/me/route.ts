import {NextResponse} from "next/server";
import {getBrowserUser} from "@/lib/auth";
export async function GET(){
 const u=await getBrowserUser();if(!u)return NextResponse.json({user:null},{status:401});
 return NextResponse.json({user:{id:u.id,username:u.username,email:u.email,equippedSkin:u.equippedSkin}});
}
