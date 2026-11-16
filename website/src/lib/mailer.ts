import nodemailer from "nodemailer";
function esc(v:string){return v.replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]!));}
export async function sendVerificationEmail(email:string,username:string,token:string){
 const host=process.env.SMTP_HOST,port=process.env.SMTP_PORT,user=process.env.SMTP_USER,pass=process.env.SMTP_PASSWORD,from=process.env.SMTP_FROM;
 const app=process.env.APP_URL??"http://localhost:3000";
 if(!host||!port||!user||!pass||!from){
  if(process.env.NODE_ENV!=="production"){
   console.info("[PROJECT VISUAL] Development verification URL:",app+"/api/auth/verify?token="+encodeURIComponent(token));
   return;
  }
  throw new Error("Email delivery is not configured.");
 }
 const link=app+"/api/auth/verify?token="+encodeURIComponent(token);
 const transport=nodemailer.createTransport({host,port:Number(port),secure:Number(port)===465,auth:{user,pass}});
 await transport.sendMail({from,to:email,subject:"Confirm your PROJECT VISUAL account",
  text:"Hi "+username+", confirm your email: "+link,
  html:"<div style='font-family:Arial;background:#10121c;color:#f4f0ff;padding:32px'><h1>PROJECT VISUAL</h1><p>Hi "+esc(username)+", confirm your email address.</p><p><a href='"+link+"' style='color:#c3a5ff'>Confirm email</a></p><p>This link expires in 24 hours.</p></div>"});
}
