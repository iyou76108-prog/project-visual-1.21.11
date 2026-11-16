import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
export async function GET(request:Request,context:{params:Promise<{gameId:string}>}){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const {gameId}=await context.params,game=await prisma.gameMatch.findUnique({where:{id:gameId}});
 if(!game||(game.player1Id!==u.id&&game.player2Id!==u.id))return NextResponse.json({error:"Match not found."},{status:404});
 return NextResponse.json({game});
}
