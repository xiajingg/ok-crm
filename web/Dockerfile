# ---------- 构建阶段 ----------
FROM node:22-alpine AS build

WORKDIR /app

# 先只拷贝依赖清单，利用 Docker 层缓存，改业务代码时不必重装依赖
COPY package.json ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
