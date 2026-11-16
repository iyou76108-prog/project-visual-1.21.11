import {NextResponse} from "next/server";
import bcrypt from "bcryptjs";
import {z} from "zod";
import {prisma} from "@/lib/prisma";
import {randomSecret,hashSecret,safeUsername} from "@/lib/security";
import {sendVerificationEmail} from "@/lib/mailer";
const schema=z.object({email:z.string().email().max(254),username:z.string().refine(safeUsername),password:z.string().min(10).max(128)});
export async function POST(request:Request){
 try{
  const parsed=schema.safeParse(await request.json());
  if(!parsed.success)return NextResponse.json({error:"Проверь почту, имя пользователя и пароль (минимум 10 символов)."},{status:400});
  const email=parsed.data.email.trim().toLowerCase(),username=parsed.data.username.trim();
  const exists=await prisma.user.findFirst({where:{OR:[{email},{username}]},select:{id:true}});
  if(exists)return NextResponse.json({error:"Почта или имя пользователя уже заняты."},{status:409});
  const passwordHash=await bcrypt.hash(parsed.data.password,12);
  const user=await prisma.user.create({data:{email,username,passwordHash}});
  const raw=randomSecret(32);
  await prisma.verificationToken.create({data:{userId:user.id,tokenHash:hashSecret(raw),expiresAt:new Date(Date.now()+86400000)}});
  try{await sendVerificationEmail(email,username,raw);}
  catch{
   if(process.env.NODE_ENV==="production")return NextResponse.json({error:"Аккаунт создан, но отправка письма временно недоступна. Обратись к администратору."},{status:503});
   return NextResponse.json({message:"Аккаунт создан. SMTP не настроен — ссылка подтверждения выведена в консоль локального сервера."},{status:201});
  }
  return NextResponse.json({message:"Аккаунт создан. Проверь почту и перейди по ссылке подтверждения."},{status:201});
 }catch{return NextResponse.json({error:"Не удалось создать аккаунт. Проверь настройки базы данных."},{status:500});}
}
