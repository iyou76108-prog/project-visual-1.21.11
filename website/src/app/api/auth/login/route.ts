import {NextRequest,NextResponse} from "next/server";
import bcrypt from "bcryptjs";
import {z} from "zod";
import {prisma} from "@/lib/prisma";
import {setBrowserSession} from "@/lib/auth";
const schema=z.object({email:z.string().email(),password:z.string().min(1).max(128)});
export async function POST(request:NextRequest){
 const parsed=schema.safeParse(await request.json().catch(()=>null));
 if(!parsed.success)return NextResponse.json({error:"Неверная почта или пароль."},{status:400});
 const user=await prisma.user.findUnique({where:{email:parsed.data.email.trim().toLowerCase()}});
 if(!user||!(await bcrypt.compare(parsed.data.password,user.passwordHash)))return NextResponse.json({error:"Неверная почта или пароль."},{status:401});
 if(!user.emailVerifiedAt)return NextResponse.json({error:"Сначала подтверди почту по ссылке из письма."},{status:403});
 const response=NextResponse.json({ok:true,username:user.username});
 await setBrowserSession(response,user.id);
 return response;
}
