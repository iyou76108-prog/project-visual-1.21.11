import {NextResponse} from "next/server";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
export async function POST(request:Request,context:{params:Promise<{gameId:string}>}){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const {gameId}=await context.params;
 const game=await prisma.gameMatch.findFirst({where:{id:gameId,player2Id:u.id,status:"INVITED"}});
 if(!game)return NextResponse.json({error:"Приглашение не найдено."},{status:404});
 const updated=await prisma.gameMatch.update({where:{id:game.id},data:{status:"ACTIVE"}});
 return NextResponse.json({game:updated});
}
