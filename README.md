# blogd

API REST de blog feita com **Java 27**, **Spring Boot 4.2** e **MariaDB**. O projeto gerencia usuários, autenticação com JWT, posts com coautoria, visibilidade pública ou privada, anexos, posts relacionados e exclusão colaborativa.

É um backend: não inclui interface web, renderização de HTML nem armazenamento de arquivos. Os posts são entregues como dados (Markdown ou reStructuredText) para que um cliente os apresente.

## O que é esse projeto?

O `blogd` é uma API para publicar e manter um blog. Ela oferece:

- cadastro e gerenciamento de usuários, com os papéis `USER` e `ADMIN`;
- login com access token JWT e refresh token revogável;
- posts com um autor principal e zero ou mais coautores;
- visibilidade `PUBLIC` ou `PRIVATE`, com chave de acesso para posts privados;
- anexos do tipo `IMAGE`, `VIDEO` ou `FILE`, referenciados por URL;
- relacionamento entre posts;
- exclusão de posts compartilhados por votação entre os autores;
- migrações de banco versionadas com Flyway.

### Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 27 |
| Framework | Spring Boot 4.2 (Web MVC, Security, Data JPA, Validation) |
| Banco | MariaDB, com migrações Flyway |
| Autenticação | JWT RSA (Spring Security OAuth2 Resource Server) + refresh tokens opacos |
| Build | Gradle 9.9.2 (wrapper incluído) |
| Testes | JUnit 5, MockMvc, Mockito e AssertJ |

## Qual problema ele resolve?

Um blog costuma tratar cada post como propriedade de uma única pessoa. Este projeto modela explicitamente situações que esse modelo não cobre bem:

- **Autoria compartilhada:** um post pode ter vários responsáveis, mas precisa de alguém que controle a lista de autores.
- **Rascunhos privados:** nem todo post deve aparecer publicamente. Um post privado pode ser lido pelos autores ou por quem tiver a chave de acesso, sem expor sua existência para os demais.
- **Exclusão de conteúdo compartilhado:** um coautor não pode apagar sozinho o trabalho dos outros. A exclusão exige aceite da maioria, e empates são decididos pelo autor principal.
- **Continuidade quando uma conta é excluída:** a autoria de um post não pode ficar órfã. O coautor mais antigo assume, ou o post é removido se não houver outro autor.
- **Sessões revogáveis:** o access token é curto, e o refresh token pode ser revogado no logout ou quando há indício de reutilização.

O foco é o backend e as regras de domínio. Não é um CMS completo e não substitui uma solução como WordPress quando o objetivo é ter site, temas e editor visual prontos.

## Como funciona?

### Visão geral

A aplicação segue o fluxo `Controller → Service → Repository`, com DTOs imutáveis (records), validação na borda e regras de negócio nos services:

```text
Cliente HTTP
    │  Basic (somente no login) ou Bearer JWT
    ▼
Controller ── valida o payload ──► Service ── aplica as regras ──► Repository ──► MariaDB
    │                                  │
    └── DTOs de entrada e saída        └── Spring Security, autoria, visibilidade e exclusão
```

As migrações em `src/main/resources/db/migration` são aplicadas no início da execução. O Hibernate está configurado com `ddl-auto=validate`, então ele apenas valida o esquema; quem o altera é o Flyway.

### Autenticação e sessão

- `POST /api/users/create` é público e cria usuários com papel `USER` por padrão. Um pedido não autenticado não consegue criar `ADMIN`, mesmo enviando `"role": "ADMIN"` no corpo.
- `POST /api/auth/login` usa **HTTP Basic** (`-u usuario:senha`). A resposta contém:
  - `accessToken`: JWT assinado com RSA, válido por **15 minutos** (900 segundos);
  - `refreshToken`: token opaco, válido por **7 dias** (604800 segundos);
  - `tokenType`: `Bearer`;
  - `expiresIn`: validade do access token em segundos;
  - `user`: dados públicos do usuário.
- As rotas protegidas esperam `Authorization: Bearer <accessToken>`.
- O JWT carrega `sub`/`iss` (username), `userId` e `scopes` (`ROLE_USER` ou `ROLE_ADMIN`).
- O refresh token é guardado no banco apenas como hash SHA-256. Cada uso o rotaciona; reutilizar um token já rotacionado revoga todos os refresh tokens daquele usuário.
- `POST /api/auth/logout` revoga o refresh token informado. O access token atual continua válido até expirar, porque JWT é stateless.
- Senhas são armazenadas com BCrypt. A política exige de 8 a 72 caracteres, com maiúscula, minúscula, número e caractere especial.
- O par de chaves RSA é lido de `classpath:app.key` e `classpath:app.pub`. Esses arquivos são ignorados pelo Git e precisam ser gerados localmente.

### Usuários e permissões

| Papel | Pode |
| --- | --- |
| `USER` | Gerenciar a própria conta e participar de posts conforme a autoria. |
| `ADMIN` | Tudo que um `USER` pode, além de listar, editar e excluir qualquer usuário e definir papéis. |

`ADMIN` **não** tem atalho sobre posts: ler um post privado, editá-lo ou excluí-lo continua dependendo de autoria ou da chave de acesso. O papel serve para administrar usuários.

Ao excluir a conta do autor principal de um post, o coautor mais antigo (menor `id`) assume a autoria. Se não houver coautor, o post é excluído. Quando um coautor exclui a conta, ele apenas sai da lista de coautores.

### Posts

Campos principais de um post:

| Campo | Regra |
| --- | --- |
| `title` | Obrigatório, até 255 caracteres. |
| `content` | Obrigatório, sem limite prático (coluna `LONGTEXT`). |
| `format` | `MARKDOWN` (padrão) ou `RST`. |
| `visibility` | `PUBLIC` (padrão) ou `PRIVATE`. |
| `authorId` | Obrigatório; na criação precisa ser o próprio usuário autenticado. |
| `coauthorIds` | Opcional; o autor principal é removido automaticamente da lista se aparecer nela. |
| `relatedPostIds` | Opcional; só aceita posts que o solicitante pode ler, e um post não pode se relacionar consigo mesmo. |
| `attachments` | Opcional; cada anexo tem `type`, `url` HTTP(S) e `name` opcional. Não há upload de arquivo. |

**Visibilidade e chave**

- Todo post privado recebe uma chave gerada pelo servidor: 32 bytes aleatórios em base64url, com 43 caracteres.
- A chave não é enviada pelo cliente na criação. Ela aparece no JSON somente para os autores do post.
- Para ler um post privado do qual você não é autor, envie a chave no header `X-Post-Key`.
- Sem a chave, ou com a chave errada, a resposta é **404**, para não revelar que o post existe.
- Quem recebeu a chave e não participa do post consegue lê-lo pela URL direta, mas ele não aparece na listagem.

**Listagem e edição**

- `GET /api/posts` retorna os posts públicos e os posts privados dos quais você é autor ou coautor.
- Qualquer autor pode editar o conteúdo, mas o autor principal é o único que pode alterar a lista de autores.
- Ao remover um autor, o aceite de exclusão daquele autor também é descartado.
- Se um post privado se tornar público, a chave é apagada. Se voltar a ser privado, uma nova chave é gerada.

### Exclusão colaborativa

O aceite é registrado por autor:

- `PUT /api/posts/{id}/deletion-acceptance`: aceita a exclusão;
- `DELETE /api/posts/{id}/deletion-acceptance`: retira o aceite;
- `DELETE /api/posts/{id}`: exclui o post, se houver aceites suficientes; caso contrário, responde **409**.

A regra é: **mais da metade dos autores precisa aceitar**. Em um empate exato, a exclusão só é permitida se o autor principal estiver entre os que aceitaram.

| Número de autores | Aceites necessários |
| --- | --- |
| 1 | 1 |
| 2 | 2, ou 1 do autor principal |
| 3 | 2 |
| 4 | 3, ou 2 incluindo o autor principal |
| 5 | 3 |

Qualquer autor pode aceitar ou retirar o próprio aceite. Quem não é autor recebe **403**.

### Banco de dados

As tabelas principais são `users`, `refresh_tokens`, `posts`, `post_coauthors`, `post_deletion_acceptances`, `post_related_posts` e `post_attachments`. A tabela `posts` guarda o autor principal em `author_id`, a visibilidade e a chave em `access_key` (`key` é palavra reservada no banco).

### Endpoints

Todas as rotas exigem autenticação, exceto as marcadas como públicas.

| Método | Rota | Quem pode | O que faz |
| --- | --- | --- | --- |
| `POST` | `/api/users/create` | Público | Cria usuário; sem um admin autenticado, o papel é `USER`. |
| `POST` | `/api/auth/login` | Público (HTTP Basic) | Autentica e devolve access + refresh tokens. |
| `POST` | `/api/auth/refresh` | Público | Rotaciona o refresh token e emite novos tokens. |
| `POST` | `/api/auth/logout` | Público | Revoga o refresh token informado. |
| `GET` | `/api/users` | `ADMIN` | Lista usuários. |
| `GET` | `/api/users/{id}` | `ADMIN` ou o próprio | Consulta um usuário. |
| `PUT` | `/api/users/{id}` | `ADMIN` ou o próprio | Atualiza dados da conta; só `ADMIN` altera o papel. |
| `PUT` | `/api/users/{id}/password` | O próprio | Troca a senha, exigindo a senha atual. |
| `DELETE` | `/api/users/{id}` | `ADMIN` ou o próprio | Exclui a conta e trata a autoria dos posts. |
| `GET` | `/api/posts` | Autenticado | Lista posts visíveis para o solicitante. |
| `GET` | `/api/posts/{id}` | Post público: qualquer autenticado; privado: autor ou chave válida | Consulta um post; privado sem chave responde 404. |
| `POST` | `/api/posts` | Autenticado | Cria um post cujo autor principal é o próprio solicitante. |
| `PUT` | `/api/posts/{id}` | Autor | Edita o post; só o autor principal altera autores. |
| `DELETE` | `/api/posts/{id}` | Autor, com maioria de aceites | Exclui o post ou responde 409. |
| `PUT` | `/api/posts/{id}/deletion-acceptance` | Autor | Registra o aceite de exclusão. |
| `DELETE` | `/api/posts/{id}/deletion-acceptance` | Autor | Retira o aceite de exclusão. |

### Erros

| Status | Quando acontece | Corpo típico |
| --- | --- | --- |
| `400` | Payload inválido ou URL de anexo inválida. | Mapa `campo → mensagem`, ou `{"error": "..."}`. |
| `401` | Sem token, token inválido, credenciais erradas ou refresh token inválido. | `{"error": "..."}` ou o formato padrão do Spring Security. |
| `403` | Autenticado, mas sem permissão (não é autor, não é o próprio usuário, não é admin). | Corpo padrão do Spring Security. |
| `404` | Recurso inexistente ou post privado sem chave válida. | `{"error": "Post not found with id: 1"}`. |
| `409` | Username já cadastrado ou exclusão sem aceites suficientes. | `{"error": "..."}`. |

## Como executar?

### Pré-requisitos

- **JDK 27** instalado (`java -version`). O projeto usa toolchain de Java 27.
- **Docker** com o plugin `docker compose`, caso queira o banco automático.
- Ou um **MariaDB** acessível, se preferir usar um banco próprio.
- **OpenSSL** para gerar o par de chaves JWT.
- `curl` e `python3` para executar os exemplos deste README.
- A porta **8081** livre (ou escolha outra, como mostrado adiante).

O Gradle não precisa estar instalado: use o wrapper `./gradlew`. Na primeira execução, ele baixa o Gradle 9.9.2.

### 1. Clone o repositório

```bash
git clone https://github.com/onlyvitor/blogd.git
cd blogd
```

### 2. Gere as chaves JWT

O repositório **não inclui** `app.key` nem `app.pub`; ambos estão no `.gitignore`. Sem eles, a aplicação e os testes não iniciam.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out src/main/resources/app.key

openssl rsa -in src/main/resources/app.key -pubout \
  -out src/main/resources/app.pub
```

Isso cria uma chave privada PKCS#8 (`BEGIN PRIVATE KEY`) e a chave pública correspondente (`BEGIN PUBLIC KEY`), que é o formato esperado pela configuração do Spring. Não versione esses arquivos e não os compartilhe.

### 3. Suba o banco de dados

Escolha **uma** das opções abaixo.

#### Opção A — Docker Compose automático (mais simples)

Com o Docker em execução, não é preciso configurar banco nem exportar variáveis. O Spring Boot detecta o `compose.yaml`, sobe o MariaDB e usa os dados de conexão do container. As variáveis `DB_*` são ignoradas nesse modo.

O banco de desenvolvimento do Compose usa:

| Item | Valor |
| --- | --- |
| Database | `mydatabase` |
| Usuário | `myuser` |
| Senha | `secret` |
| Senha do root | `verysecret` |
| Porta no host | aleatória, escolhida pelo Docker |

São credenciais de desenvolvimento. Não as use em produção.

#### Opção B — MariaDB existente

Crie o banco e um usuário, se ainda não existirem:

```bash
mariadb -u root -p <<'SQL'
CREATE DATABASE blogd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'blogd'@'%' IDENTIFIED BY 'troque-esta-senha';
GRANT ALL PRIVILEGES ON blogd.* TO 'blogd'@'%';
FLUSH PRIVILEGES;
SQL
```

Depois exporte as variáveis no shell (ou configure-as na IDE):

```bash
export DB_NAME=blogd
export DB_USERNAME=blogd
export DB_PASSWORD='troque-esta-senha'

# Impede o Spring Boot de subir o MariaDB do compose.yaml e sobrepor a conexão.
export SPRING_DOCKER_COMPOSE_ENABLED=false
```

A URL padrão é `jdbc:mariadb://localhost:3306/${DB_NAME}`. Para outro host ou porta, sobrescreva:

```bash
export SPRING_DATASOURCE_URL='jdbc:mariadb://localhost:3307/blogd'
```

**Atenção:** o projeto **não carrega** `.env` automaticamente. O `.env.example` é apenas uma referência e usa espaços ao redor do `=`, então `source .env` não funciona como está. Exporte as variáveis no shell ou configure-as na IDE.

### 4. Inicie a aplicação

```bash
./gradlew bootRun
```

Na primeira execução, o Flyway aplica as migrações V1 a V7. Quando o log mostrar `Started BlogdApplication`, a API estará em:

```text
http://localhost:8081
```

Para usar outra porta:

```bash
SERVER_PORT=8082 ./gradlew bootRun
```

### 5. Crie o primeiro administrador

O cadastro público cria apenas `USER`. Não existe endpoint para criar o primeiro `ADMIN`; promova um usuário existente pelo banco:

```bash
mariadb -u "$DB_USERNAME" -p "$DB_NAME" \
  -e "UPDATE users SET role = 'ADMIN' WHERE username = 'alice';"
```

Se estiver usando o banco do Compose, execute o comando na raiz do repositório (o projeto do Compose recebe o nome do diretório):

```bash
docker compose exec mariadb mariadb -umyuser -psecret mydatabase \
  -e "UPDATE users SET role = 'ADMIN' WHERE username = 'alice';"
```

Se o serviço não for encontrado, localize o container com `docker ps --filter name=mariadb` e use `docker exec` no lugar de `docker compose exec`.

Faça login novamente para receber um token com `ROLE_ADMIN`.

### 6. Rode os testes

Os testes precisam das chaves JWT e de um MariaDB acessível. Eles aplicam as migrações e criam/removem seus próprios dados, mas o ideal é usar um banco separado do banco de desenvolvimento.

Uma opção é subir um banco descartável:

```bash
docker run -d --name blogd-test-db \
  -e MARIADB_ROOT_PASSWORD=root \
  -e MARIADB_DATABASE=blogd_test \
  -e MARIADB_USER=blogd \
  -e MARIADB_PASSWORD=blogd \
  -p 127.0.0.1:3307:3306 \
  mariadb:latest

# Espere o banco aceitar conexões antes de rodar os testes.
until docker exec blogd-test-db mariadb -ublogd -pblogd blogd_test \
  -e 'SELECT 1' >/dev/null 2>&1; do sleep 2; done

export DB_NAME=blogd_test
export DB_USERNAME=blogd
export DB_PASSWORD=blogd
export SPRING_DATASOURCE_URL='jdbc:mariadb://localhost:3307/blogd_test'
export SPRING_DOCKER_COMPOSE_ENABLED=false

./gradlew test
```

Para forçar a reexecução quando o Gradle considerar a tarefa atualizada:

```bash
./gradlew cleanTest test
```

No estado atual, a suíte tem **90 testes**. Depois, remova o banco descartável:

```bash
docker rm -f blogd-test-db
```

### Problemas comuns

| Sintoma | Causa provável | Solução |
| --- | --- | --- |
| `class path resource [app.key] cannot be opened` | Chaves JWT ausentes. | Execute o passo 2. |
| `Could not resolve placeholder 'DB_NAME'` | Banco próprio sem as variáveis exportadas. | Exporte `DB_NAME`, `DB_USERNAME` e `DB_PASSWORD`, ou use o Compose. |
| `Connection refused` ao conectar no MariaDB | Banco parado, porta errada ou host errado. | Suba o banco e confira `SPRING_DATASOURCE_URL`. |
| `Access denied for user` | Usuário/senha incorretos. | Confira `DB_USERNAME` e `DB_PASSWORD`. |
| `FlywayValidateException` ou erro de checksum | Migração já aplicada foi editada. | Não edite migrações aplicadas; crie uma nova versão `V8__...sql`. Em desenvolvimento, recrie o banco. |
| `Port 8081 was already in use` | Outra aplicação está na porta. | Use `SERVER_PORT=8082 ./gradlew bootRun`. |
| Docker não encontrado ou Compose não inicia | Docker parado ou sem o plugin. | Inicie o Docker ou use a opção B com `SPRING_DOCKER_COMPOSE_ENABLED=false`. |
| `No enum constant ... USUARIO` | Banco antigo com o papel `USUARIO`, renomeado para `USER`. | Veja a seção de atualização abaixo. |

### Atualizando um banco existente

Se você já tinha um banco de uma versão anterior deste projeto, **faça backup antes**:

```bash
mariadb-dump -u "$DB_USERNAME" -p "$DB_NAME" > backup-$(date +%F).sql
```

O papel `USUARIO` foi renomeado para `USER` no código, sem migração automática. Antes de iniciar a versão nova, ajuste as linhas existentes:

```bash
mariadb -u "$DB_USERNAME" -p "$DB_NAME" \
  -e "UPDATE users SET role = 'USER' WHERE role = 'USUARIO';"
```

A migração **V7** também altera dados de posts de forma irreversível:

- escolhe como autor principal o usuário de menor `id` em `post_authors`;
- **exclui posts que não têm nenhum autor**;
- copia os demais autores para `post_coauthors`;
- remove a tabela `post_authors`.

Sem o backup, não há como recuperar os posts excluídos por essa migração.

## Como contribuir ou utilizar?

### Usando a API

Os exemplos abaixo consideram a aplicação em `http://localhost:8081` e um banco novo. Ajuste os IDs conforme as respostas.

**1. Crie dois usuários**

```bash
BASE=http://localhost:8081

curl -s -X POST "$BASE/api/users/create" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Senha@123","email":"alice@example.com"}'

curl -s -X POST "$BASE/api/users/create" \
  -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"Senha@123","email":"bob@example.com"}'
```

Em um banco novo, as respostas trazem `"id": 1` para Alice e `"id": 2` para Bob.

**2. Faça login e guarde o access token**

```bash
TOKEN=$(curl -s -u alice:'Senha@123' -X POST "$BASE/api/auth/login" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

echo "$TOKEN"
```

A resposta completa do login tem o formato:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "3EWR95FBL1P4...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "role": "USER"
  }
}
```

**3. Crie um post privado com Bob como coautor**

```bash
curl -s -X POST "$BASE/api/posts" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Rascunho privado",
    "content": "Conteúdo do post",
    "format": "MARKDOWN",
    "visibility": "PRIVATE",
    "authorId": 1,
    "coauthorIds": [2],
    "relatedPostIds": [],
    "attachments": [
      {
        "type": "IMAGE",
        "url": "https://example.com/capa.png",
        "name": "capa"
      }
    ]
  }'
```

A resposta (resumida) inclui o `id` do post e a chave, visível porque Alice é autora:

```json
{
  "id": 1,
  "visibility": "PRIVATE",
  "key": "Q369QEwndSc5wAk4kx0Txe_8ocjnoTDLeKeTdL_LLtk",
  "author": {"id": 1, "username": "alice"},
  "coauthors": [{"id": 2, "username": "bob"}],
  "deletable": false
}
```

**4. Leia o post privado**

Bob é coautor, então consegue ler sem a chave:

```bash
BOB_TOKEN=$(curl -s -u bob:'Senha@123' -X POST "$BASE/api/auth/login" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -s "$BASE/api/posts/1" \
  -H "Authorization: Bearer $BOB_TOKEN"
```

Já um usuário que não participa do post precisa da chave. Substitua `$OUTRO_TOKEN` pelo access token de um terceiro usuário autenticado:

```bash
curl -s "$BASE/api/posts/1" \
  -H "Authorization: Bearer $OUTRO_TOKEN" \
  -H "X-Post-Key: Q369QEwndSc5wAk4kx0Txe_8ocjnoTDLeKeTdL_LLtk"
```

Sem a chave, ou com a chave errada, a resposta é `404`. A chave não aparece no JSON de quem não é autor.

**5. Liste os posts visíveis**

```bash
curl -s "$BASE/api/posts" \
  -H "Authorization: Bearer $BOB_TOKEN"
```

A listagem inclui posts públicos e posts privados dos quais o solicitante é autor ou coautor. Posts privados liberados apenas por chave não aparecem na lista.

**6. Aceite a exclusão e exclua o post**

```bash
# Bob aceita
curl -s -X PUT "$BASE/api/posts/1/deletion-acceptance" \
  -H "Authorization: Bearer $BOB_TOKEN"

# Alice aceita
curl -s -X PUT "$BASE/api/posts/1/deletion-acceptance" \
  -H "Authorization: Bearer $TOKEN"

# Alice exclui
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE "$BASE/api/posts/1" \
  -H "Authorization: Bearer $TOKEN"
```

A última chamada responde `204`. Sem aceites suficientes, responderia `409`.

**7. Renove ou encerre a sessão**

```bash
# Renova: o refresh token usado é rotacionado e deixa de valer.
REFRESH=$(curl -s -u alice:'Senha@123' -X POST "$BASE/api/auth/login" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["refreshToken"])')

curl -s -X POST "$BASE/api/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# Para sair, use um refresh token que ainda não foi rotacionado.
REFRESH=$(curl -s -u alice:'Senha@123' -X POST "$BASE/api/auth/login" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["refreshToken"])')

curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/auth/logout" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

O `refresh` devolve um novo par de tokens e invalida o refresh token usado. O `logout` revoga o token informado e responde `204`. Se você já renovou a sessão, o token antigo não serve mais; use o refresh token novo.

### Contribuindo

1. Faça um fork e crie uma branch a partir de `main`.
2. Mantenha o estilo de commits do histórico: mensagens em inglês no formato Conventional Commits (`feat(post): ...`, `fix(auth): ...`, `test(user): ...`, `docs: ...`), com o corpo em tópicos quando fizer sentido.
3. Gere as chaves JWT e rode `./gradlew test` antes de abrir o pull request.
4. Inclua testes para comportamentos novos ou corrigidos. A suíte atual cobre autenticação, usuários, posts, permissões, visibilidade e exclusão.
5. Nunca envie `app.key`, `app.pub`, `.env`, tokens ou senhas reais.
6. Migrações do Flyway são imutáveis depois de aplicadas. Crie `V8__descricao.sql`, `V9__descricao.sql` e assim por diante; não edite V1–V7.
7. Descreva no pull request o problema, a solução e como você testou. PRs grandes devem ser discutidos antes em uma issue.

Não há pipeline de CI configurado no repositório. A verificação esperada é a suíte local passando.

### Licença e reutilização

Não existe um arquivo `LICENSE` no repositório. Até que uma licença seja definida, o código não concede permissão explícita de reutilização, distribuição ou uso comercial. Se quiser usar o projeto como base, entre em contato com o autor para combinar os termos.
