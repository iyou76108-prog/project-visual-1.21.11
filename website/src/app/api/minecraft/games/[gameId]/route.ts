import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";

export async function GET(request:Request,context:{params:Promise<{gameId:string}>}){
 const user=await getMinecraftUser(request);
 if(!user)return NextResponse.json({error:"Not authenticated"},{status:401});
 const {gameId}=await context.params;
 const game=await prisma.gameMatch.findUnique({
  where:{id:gameId},
  include:{player1:{select:{username:true,equippedSkin:true}},player2:{select:{username:true,equippedSkin:true}}}
 });
 if(!game||(game.player1Id!==user.id&&game.player2Id!==user.id))
  return NextResponse.json({error:"Match not found."},{status:404});
 return NextResponse.json({game});
}
