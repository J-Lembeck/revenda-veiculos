@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0infra"

docker info >nul 2>&1
if errorlevel 1 (
    echo O Docker não está rodando: abra o Docker Desktop e rode de novo.
    exit /b 1
)

if not exist .env (
    copy /y .env.example .env >nul
    echo Criado infra\.env com os valores de desenvolvimento.
)

echo Subindo o PostgreSQL, o LocalStack, o Keycloak e os quatro serviços.
echo A primeira vez leva alguns minutos: o Docker baixa as imagens e compila os serviços.
docker compose --profile apps up -d --build --wait
if errorlevel 1 (
    echo A subida falhou. Veja o que aconteceu com: docker compose --profile apps logs, na pasta infra
    exit /b 1
)

echo.
echo Pronto: os sete contêineres estão no ar e saudáveis.
echo.
echo   Swagger UI   http://localhost:8081/swagger-ui.html (estoque)
echo                http://localhost:8082/swagger-ui.html (clientes)
echo                http://localhost:8083/swagger-ui.html (compras)
echo   Keycloak     http://localhost:8180 (usuários e senhas de desenvolvimento em infra\keycloak\realm.json)
echo.
echo   Postman      importe postman\revenda-compra.postman_collection.json (sem ambiente)
echo                e clique em Run collection: a compra inteira, do login ao documento
echo.
echo   Parar        na pasta infra: docker compose --profile apps down
