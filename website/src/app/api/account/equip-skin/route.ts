import {NextResponse} from "next/server";
import {z} from "zod";
import {getBrowserUser} from "@/lib/auth";
import {prisma} from "@/lib/prisma";

const schema=z.object({skinId:z.string().min(1).max(64).nullable()});
export async function POST(request:Request){
 const user=await getBrowserUser();
 if(!user)return NextResponse.json({error:"Войди в подтверждённый аккаунт."},{status:401});
 const body=schema.safeParse(await request.json().catch(()=>null));
 if(!body.success)return NextResponse.json({error:"Некорректный скин."},{status:400});
 const skinId=body.data.skinId;
 if(skinId){
  const owned=await prisma.inventoryItem.findFirst({where:{userId:user.id,skinId},select:{id:true}});
  if(!owned)return NextResponse.json({error:"Этот скин отсутствует в твоём инвентаре."},{status:403});
 }
 const updated=await prisma.user.update({
  where:{id:user.id},data:{equippedSkin:skinId},select:{equippedSkin:true}
 });
 return NextResponse.json({ok:true,equippedSkin:updated.equippedSkin});
}
