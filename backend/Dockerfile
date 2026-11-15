FROM node:22-bookworm-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ && rm -rf /var/lib/apt/lists/*
COPY package.json ./
RUN npm install --omit=dev
COPY server.mjs ./
RUN mkdir -p /app/data && chown -R node:node /app
USER node
ENV PORT=8080
ENV PV_DB_PATH=/app/data/project-visual.sqlite
EXPOSE 8080
CMD ["npm","start"]
