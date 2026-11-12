#!/bin/sh
set -eu

cd "$(dirname "$0")/infra"

if ! docker info >/dev/null 2>&1; then
    echo "O Docker não está rodando: abra o Docker Desktop (ou inicie o serviço do Docker) e rode de novo." >&2
    exit 1
fi

if [ ! -f .env ]; then
    cp .env.example .env
    echo "Criado infra/.env com os valores de desenvolvimento."
fi

echo "Subindo o PostgreSQL, o LocalStack, o Keycloak e os quatro serviços."
echo "A primeira vez leva alguns minutos: o Docker baixa as imagens e compila os serviços."
if ! docker compose --profile apps up -d --build --wait; then
    echo "A subida falhou. Veja o que aconteceu com: cd infra && docker compose --profile apps logs" >&2
    exit 1
fi

cat <<EOF

Pronto: os sete contêineres estão no ar e saudáveis.

  Swagger UI   http://localhost:8081/swagger-ui.html (estoque)
               http://localhost:8082/swagger-ui.html (clientes)
               http://localhost:8083/swagger-ui.html (compras)
  Keycloak     http://localhost:8180 (usuários e senhas de desenvolvimento em infra/keycloak/realm.json)

  Postman      importe postman/revenda-compra.postman_collection.json (sem ambiente)
               e clique em Run collection: a compra inteira, do login ao documento

  Parar        cd infra && docker compose --profile apps down
EOF
