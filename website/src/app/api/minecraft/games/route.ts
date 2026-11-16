import {NextResponse} from "next/server";
import {GameType} from "@prisma/client";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
const START_FEN="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
export async function GET(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const games=await prisma.gameMatch.findMany({where:{OR:[{player1Id:u.id},{player2Id:u.id}],status:{in:["INVITED","ACTIVE"]}},
 include:{player1:{select:{username:true}},player2:{select:{username:true}}},orderBy:{updatedAt:"desc"}});
 return NextResponse.json({games});
}
export async function POST(request:Request){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const body=await request.json().catch(()=>null) as {username?:string;type?:string}|null;
 if(!body?.username||!["CHESS","TIC_TAC_TOE"].includes(body.type??""))return NextResponse.json({error:"Выбери друга и игру."},{status:400});
 const other=await prisma.user.findUnique({where:{username:body.username}});
 if(!other||other.id===u.id)return NextResponse.json({error:"Игрок не найден."},{status:404});
 const friendship=await prisma.friendship.findFirst({where:{status:"ACCEPTED",OR:[{requesterId:u.id,addresseeId:other.id},{requesterId:other.id,addresseeId:u.id}]}});
 if(!friendship)return NextResponse.json({error:"Сначала добавь игрока в друзья."},{status:403});
 const type=body.type as GameType,state=type==="CHESS"?{fen:START_FEN}:{board:"........."};
 const game=await prisma.gameMatch.create({data:{type,status:"INVITED",player1Id:u.id,player2Id:other.id,nextTurnUserId:u.id,state}});
 return NextResponse.json({game},{status:201});
}
