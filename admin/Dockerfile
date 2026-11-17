# ==============================================================================
# NOVA PETZLIFE — Production Admin Frontend Dockerfile
# Multi-Stage: Node 20 Build -> Nginx Alpine Runtime
# ==============================================================================

# Stage 1: Build Angular admin application
FROM node:20-alpine AS builder

WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve with Nginx Alpine
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist/admin/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
