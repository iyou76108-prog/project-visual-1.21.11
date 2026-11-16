import {NextResponse} from "next/server";
import {Chess} from "chess.js";
import {getMinecraftUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";
const wins=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
const hasLine=(b:string,m:string)=>wins.some(line=>line.every(i=>b[i]===m));
export async function POST(request:Request,context:{params:Promise<{gameId:string}>}){
 const u=await getMinecraftUser(request);if(!u)return NextResponse.json({error:"Not authenticated"},{status:401});
 const {gameId}=await context.params;
 const body=await request.json().catch(()=>null) as {from?:string;to?:string;promotion?:string;index?:number;version?:number}|null;
 const game=await prisma.gameMatch.findFirst({where:{id:gameId,status:"ACTIVE"}});
 if(!game)return NextResponse.json({error:"Матч не найден или завершён."},{status:404});
 if(game.player1Id!==u.id&&game.player2Id!==u.id)return NextResponse.json({error:"Not a player."},{status:403});
 if(game.nextTurnUserId!==u.id)return NextResponse.json({error:"Сейчас ход другого игрока."},{status:409});
 if(body?.version!==undefined&&body.version!==game.version)return NextResponse.json({error:"Позиция изменилась. Обнови матч."},{status:409});
 let state:Record<string,unknown>,finished=false,winnerId:string|null=null;
 let nextTurn=game.player1Id===u.id?game.player2Id:game.player1Id;
 try{
  const current=game.state as Record<string,unknown>;
  if(game.type==="CHESS"){
   if(!body?.from||!body?.to)return NextResponse.json({error:"Укажи поля хода."},{status:400});
   const chess=new Chess(String(current.fen));
   chess.move({from:body.from,to:body.to,promotion:body.promotion as "q"|"r"|"b"|"n"|undefined});
   state={fen:chess.fen(),pgn:chess.pgn()};finished=chess.isGameOver();
   if(finished&&chess.isCheckmate())winnerId=u.id;
   nextTurn=chess.turn()==="w"?game.player1Id:game.player2Id;
  }else{
   const i=body?.index;
   if(!Number.isInteger(i)||i===undefined||i<0||i>8)return NextResponse.json({error:"Некорректная клетка."},{status:400});
   const b=String(current.board??".........").split("");
   if(b.length!==9||b[i]!==".")return NextResponse.json({error:"Клетка уже занята."},{status:409});
   const mark=game.player1Id===u.id?"X":"O";b[i]=mark;const text=b.join("");
   const won=hasLine(text,mark);finished=won||!text.includes(".");
   if(won)winnerId=u.id;state={board:text};
  }
 }catch{return NextResponse.json({error:"Нелегальный ход."},{status:400});}
 const changed=await prisma.gameMatch.updateMany({where:{id:game.id,status:"ACTIVE",version:game.version,nextTurnUserId:u.id},
  data:{state:state as never,version:{increment:1},nextTurnUserId:nextTurn,status:finished?"FINISHED":"ACTIVE",winnerId}});
 if(changed.count!==1)return NextResponse.json({error:"Матч обновился параллельно. Получи новую позицию."},{status:409});
 return NextResponse.json({game:await prisma.gameMatch.findUnique({where:{id:game.id}})});
}
