import { createHash, randomBytes } from "node:crypto";
export function randomSecret(bytes=32){return randomBytes(bytes).toString("base64url");}
export function hashSecret(value:string){return createHash("sha256").update(value).digest("hex");}
export function safeUsername(value:string){return /^[a-zA-Z0-9_]{3,16}$/.test(value);}
