import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
export const metadata:Metadata={title:"PROJECT VISUAL — Your client, your world",description:"Accounts, cosmetics, friends and minigames for PROJECT VISUAL."};
export default function RootLayout({children}:{children:React.ReactNode}){
 return <html lang="ru"><body><header className="topbar">
  <Link className="brand" href="/"><span className="brand-mark">PV</span> PROJECT VISUAL</Link>
  <nav><Link href="/cases">Кейсы</Link><Link href="/dashboard">Кабинет</Link><Link href="/login">Войти</Link></nav>
 </header><main>{children}</main><footer>PROJECT VISUAL · Minecraft 1.21.11 · Site preview — не опубликован</footer></body></html>;
}
