import { PrismaClient } from "@prisma/client";
const g=globalThis as unknown as {projectVisualPrisma?:PrismaClient};
export const prisma=g.projectVisualPrisma??new PrismaClient();
if(process.env.NODE_ENV!=="production")g.projectVisualPrisma=prisma;
