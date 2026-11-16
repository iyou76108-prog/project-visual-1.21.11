import {NextResponse} from "next/server";
import {prisma} from "@/lib/prisma";
import {hashSecret} from "@/lib/security";
export async function GET(request:Request){
 const url=new URL(request.url),raw=url.searchParams.get("token")??"";
 if(!raw||raw.length>128)return NextResponse.redirect(new URL("/login?verified=0",url));
 const row=await prisma.verificationToken.findUnique({where:{tokenHash:hashSecret(raw)}});
 if(!row||row.expiresAt<=new Date())return NextResponse.redirect(new URL("/login?verified=0",url));
 await prisma.$transaction([
  prisma.user.update({where:{id:row.userId},data:{emailVerifiedAt:new Date()}}),
  prisma.verificationToken.delete({where:{id:row.id}})
 ]);
 return NextResponse.redirect(new URL("/login?verified=1",url));
}
