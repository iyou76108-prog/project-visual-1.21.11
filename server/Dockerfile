FROM node:22-alpine

WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY src ./src

ENV HOST=0.0.0.0
ENV PORT=8787
ENV PV_DATA_FILE=/data/project-visual.json

RUN mkdir -p /data && chown -R node:node /app /data
USER node

VOLUME ["/data"]
EXPOSE 8787
CMD ["node", "src/server.js"]
