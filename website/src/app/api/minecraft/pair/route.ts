import {NextResponse} from "next/server";
import {prisma} from "@/lib/prisma";
import {hashSecret,randomSecret} from "@/lib/security";
export async function POST(request:Request){
 const body=await request.json().catch(()=>null) as {code?:string}|null;
 const code=(body?.code??"").trim().toUpperCase();
 if(!/^PV-[A-Z0-9]{6,8}$/.test(code))return NextResponse.json({error:"Неверный код подключения."},{status:400});
 const row=await prisma.minecraftPairCode.findUnique({where:{codeHash:hashSecret(code)}});
 if(!row||row.consumedAt||row.expiresAt<=new Date())return NextResponse.json({error:"Код истёк или уже использован. Создай новый на сайте."},{status:410});
 const accessToken=randomSecret(40),expiresAt=new Date(Date.now()+90*86400000);
 const user=await prisma.$transaction(async tx=>{
  const changed=await tx.minecraftPairCode.updateMany({where:{id:row.id,consumedAt:null,expiresAt:{gt:new Date()}},data:{consumedAt:new Date()}});
  if(changed.count!==1)throw new Error("USED");
  const account=await tx.user.findUniqueOrThrow({where:{id:row.userId}});
  await tx.minecraftToken.create({data:{userId:account.id,tokenHash:hashSecret(accessToken),expiresAt}});
  return account;
 }).catch(()=>null);
 if(!user)return NextResponse.json({error:"Код уже использован. Создай новый на сайте."},{status:410});
 return NextResponse.json({accessToken,userId:user.id,username:user.username,expiresAt:expiresAt.toISOString()});
}
