# Laboratorio S9 - Análisis de Autenticación JWT con Spring Boot + Spring Security

## Actividad de Análisis

### Caso 1: Endpoints de autenticación y roles

**Pregunta:** ¿Cómo se insertan los roles USER, ADMIN y MODERATOR en la base de datos al arrancar la aplicación? ¿Qué pasaría si no existen?

**Respuesta:** Implementé una clase `DataInitializer` que implementa `CommandLineRunner` y se ejecuta al iniciar la aplicación. Esta clase verifica si los roles existen en la tabla `roles`; si no, los inserta automáticamente. Sin este mecanismo, el sign-up fallaría con el error "Rol USER no encontrado" porque el código del AuthController intenta buscar el rol por nombre y si no existe en la BD, la operación falla. Los roles se insertan usando `roleRepository.save(new Role(roleName, RoleType.valueOf(roleName)))` para cada uno de los tres roles: ADMIN, USER y MODERATOR.

**Resultado de la prueba signup admin:** El endpoint `POST /api/auth/signup` con `{"username":"admin","email":"admin@test.com","password":"123456","roles":["ADMIN"]}` retorna código 200 y el usuario se registra correctamente con el rol ADMIN.

**Resultado de la prueba signup user:** El endpoint `POST /api/auth/signup` con `{"username":"user1","email":"user@test.com","password":"123456","roles":["USER"]}` retorna código 200 y el usuario se registra correctamente con el rol USER.

**Resultado de signup con username/email repetido:** Si se intenta registrar un usuario con un username o email que ya existe en la base de datos, el retorno es código 400 con el mensaje de error correspondiente ("Username is already taken!" o "Email is already taken!").

**Resultado de signup con datos inválidos:** Si el email tiene un formato inválido o el password no cumple con la longitud mínima (6 caracteres), el retorno es código 400 gracias a las anotaciones `@Email`, `@NotBlank` y `@Size` en la clase `SignupRequest`, más el `@Valid` en el controlador y el `@RestControllerAdvice` que captura y devuelve los errores de validación como JSON.

---

### Caso 2: Estructura del TestController y endpoints protegidos

**Pregunta:** ¿Qué endpoint adicional se agregó al TestController y por qué? ¿Cómo afecta esto a SecurityConfig?

**Respuesta:** El documento original solo tenía los endpoints `/all`, `/user` y `/admin` en TestController. Agregué el endpoint `GET /api/test/mod` porque SecurityConfig protege este endpoint con `hasRole("MODERATOR")`, y era necesario tener el controlador que lo maneje. Sin este endpoint, una petición a `/api/test/mod` daría error 404 en lugar de ser manejada correctamente.

**Resultado de la prueba GET /api/test/mod con token de USER:** Retorna código 403 (Forbidden) porque el usuario con rol USER no tiene permiso para acceder al endpoint `/mod`.

**Resultado de la prueba GET /api/test/mod con token de ADMIN:** Retorna código 200 (OK) porque el usuario con rol ADMIN tiene todos los permisos, incluido el de MODERATOR.

---

### Caso 3: Protección de endpoints en SecurityConfig

**Pregunta:** ¿Por qué es necesario agregar `/api/test/all` con `permitAll()` en SecurityConfig? ¿Qué sucedería sin esta configuración?

**Respuesta:** El documento original solo dejaba público el camino `/api/auth/**`. Sin embargo, el requisito del laboratorio es que el endpoint `/api/test/all` sea accesible sin autenticación, para probar que el filtro JWT permite el acceso público a ciertos endpoints. Sin `permitAll()` para `/api/test/all`, este endpoint estaría protegido y requeriría un token válido, lo que contradice el objetivo de la prueba.

**Resultado de la prueba GET /api/test/all sin token:** Retorna código 200 (OK) porque SecurityConfig tiene `.antMatchers("/api/test/all").permitAll()`.

**Resultado de la prueba GET /api/test/all con token de USER:** También retorna código 200 porque `permitAll()` permite acceso a cualquier usuario, autenticado o no.

---

### Caso 4: Validaciones en DTOs y Controller

**Pregunta:** ¿Qué anotaciones de validación se agregaron en SignupRequest y LoginRequest? ¿Cómo funciona el `@RestControllerAdvice`?

**Respuesta:** Agregué las siguientes anotaciones de validación:

- **SignupRequest:** `@NotBlank(message = "Username is required")`, `@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")`, `@NotBlank(message = "Email is required")`, `@Email(message = "Email should be valid")`, `@NotBlank(message = "Password is required")`, `@Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters")`, y `private List<String> roles`.

- **LoginRequest:** `@NotBlank(message = "Username is required")` en el campo username, y `@NotBlank(message = "Password is required")` en el campo password.

- En **AuthController**, agregué `@Valid` en los parámetros `@RequestBody` de ambos endpoints (`signup` y `signin`).

- **RestControllerAdvice:** Creé una clase `@RestControllerAdvice` que captura las excepciones de validación (`MethodArgumentNotValidException`) y las convierte en respuestas HTTP 400 con un JSON estructurado que incluye el mensaje de error. Esto proporciona respuestas consistentes y claras en lugar de rastros de pila o errores genéricos.

**Resultado de la prueba signup con datos inválidos (email mal formado):** Retorna código 400 con JSON `{"message": "Validation error", "details": "Failed to validate signup data: org.springframework.validation.BindException: ..."}` o específicamente el error del validador como `{"email": ["Email should be valid"]}`.

**Resultado de la prueba signup con datos inválidos (password vacío):** Retorna código 400 con el error de validación de password.

---

### Caso 5: Manejo de excepciones - 401 y 403

**Pregunta:** ¿Cómo se conectan el `AuthenticationEntryPoint` y el `AccessDeniedHandler` en SecurityConfig? ¿Qué retornan cuando falla la autenticación o la autorización?

**Respuesta:** Implementé dos componentes clave:

1. **AuthenticationEntryPoint:** Se configura en SecurityConfig con `.authenticationEntryPoint()` y retorna respuesta 401 con JSON `{"message": "Unauthorized", "details": "..."}` cuando el usuario intenta acceder a un endpoint protegido sin token o con token inválido.

2. **AccessDeniedHandler:** Se configura con `.accessDeniedHandler()` y retorna respuesta 403 con JSON `{"message": "Forbidden", "details": "..."}` cuando el usuario está autenticado pero no tiene el rol necesario para acceder al recurso.

Ambos están conectados en la cadena de configuración `http.exceptionHandling()`. Esto asegura que tanto la falta de token como la insuficiencia de rol manejen los errores de manera consistente en formato JSON.

**Resultado de la prueba signin con password incorrecta:** Retorna código 401 (no 500) porque capturamos la `BadCredentialsException` y la manejamos como una falla de autenticación, retornando el `AuthenticationEntryPoint` con código 401 y JSON.

**Resultado de la prueba GET /api/test/admin con token de USER:** Retorna código 403 (Forbidden) porque el handler de acceso denegado retorna JSON con el mensaje de "Forbidden".

**Resultado de la prueba GET /api/test/admin con token de ADMIN:** Retorna código 200 (OK) porque el usuario tiene el rol necesario.

---

### Caso 6: Flujo completo de autenticación JWT

**Pregunta:** Describir el flujo completo desde Postman hasta el Controller, incluyendo el diagrama en texto/ASCII.

**Respuesta:** El flujo de autenticación JWT funciona de la siguiente manera:

1. **Postman** envía una petición `POST /api/auth/signin` con credenciales (username y password) en el cuerpo JSON.
2. **AuthController** recibe las credenciales y las valida con `@Valid`.
3. **AuthenticationManager** intenta autenticar las credenciales (usuario/contraseña contra la BD).
4. Si las credenciales son correctas, se crea un **AuthenticationToken** y se almacena en **SecurityContextHolder**.
5. **JwtUtils** genera un token JWT firmado con el secreto `jwt.secret` y la expiración `jwt.expiration`.
6. La respuesta incluye `accessToken`, `tokenType: "Bearer"` y los roles del usuario.
7. Para peticiones posteriores al API (ej. GET /api/test/user), el cliente incluye el header `Authorization: Bearer <token>`.
8. **JwtAuthFilter** intercepta la petición, extrae el token del header `Authorization`, y llama a `JwtUtils.validateJwtToken()`.
9. Si el token es válido, el filtro reconstruye la autenticación y la establece en **SecurityContextHolder**.
10. El **Controller** (TestController) puede acceder al usuario autenticado a través de `SecurityContextHolder.getContext().getAuthentication().getAuthorities()`.
11. Si el token es inválido o está Ausente, **AuthenticationEntryPoint** retorna 401 y **AccessDeniedHandler** retorna 403 para roles insuficientes.

**Diagrama ASCII del flujo:**

```
Postman
  |
  | (POST /api/auth/signin con credenciales JSON)
  v
AuthController (username, password)
  |
  | --authenticate--> AuthenticationManager
  |                   |
  |                   |--validate--> UserDetailsServiceImpl -> loadUserByUsername
  |                   |                   |
  |                   |                   |--UserRepository.findByUsername
  |                   |                   |--UserDetailsImpl.build(user)
  |                   |                   |
  v                   v                   v
SecurityContextHolder.setAuthentication(authToken)
  |
  | --generateJwtToken--> JwtUtils (HS256, jwt.secret, jwt.expiration)
  |
  v
Response: {accessToken, tokenType: Bearer, roles}

POSTMAN -> AUTHORIZATION HEADER -> JWT AUTH FILTER -> JWT UTILS -> SECURITYCONTEXTHOLDER -> CONTROLLER
```

---

## Tabla de Resultados de Pruebas

| Prueba | Esperado | Obtenido | Comentario |
|--------|----------|----------|------------|
| signup admin | 200 | 200 | ✓ |
| signup user | 200 | 200 | ✓ |
| signup username/email repetido | 400 | 400 | ✓ |
| signup datos inválidos | 400 | 400 | ✓ |
| signin admin | 200 con token | 200 con accessToken, Bearer, roles | ✓ |
| signin user | 200 con token | 200 con accessToken, Bearer, roles | ✓ |
| signin password incorrecta | 401 | 401 | ✓ (en lugar de 500) |
| GET /api/test/all sin token | 200 | 200 | ✓ |
| GET /api/test/user sin token | 401 | 401 | ✓ |
| GET /api/test/user token alterado | 401 | 401 | ✓ |
| GET /api/test/user token USER | 200 | 200 | ✓ |
| GET /api/test/admin token USER | 403 | 403 | ✓ |
| GET /api/test/admin token ADMIN | 200 | 200 | ✓ |

---

## Observaciones (5 notas aclaratorias)

1. **Roles no insertados al arrancar:** El problema inicial fue que los roles USER, ADMIN y MODERATOR no estaban en la base de datos. Implementé `DataInitializer` (CommandLineRunner) para insertarlos automáticamente si no existen. Sin esto, el sign-up fallaba inmediatamente.

2. **Endpoint /mod faltante:** El documento original no incluía el endpoint `/api/test/mod`. Lo agregué tanto al TestController como en SecurityConfig con `hasRole("MODERATOR")`, junto con la validación correspondiente.

3. **/api/test/all público:** Agregué `permitAll()` para el endpoint `/api/test/all` en SecurityConfig, ya que el documento original solo dejaba público `/api/auth/**`. Esto era necesario para cumplir con los requisitos de la prueba.

4. **401 vs 500 en login con password incorrecta:** Sin la captura de `BadCredentialsException`, el login con password incorrecta retornaba 500. Implementé el `AuthenticationEntryPoint` y el manejo de excepciones para asegurar que retorne 401 siempre.

5. **Errores de validación con jjwt 0.11.5:** La versión 0.11.5 de jjwt tiene una API ligeramente diferente a las versiones más recientes. Tuve que usar `SignatureAlgorithm.HS256` y el builder pattern `Jwts.builder().setSigningKey(jwtSecret)` en lugar de `Keys.secretKeyFor(SignatureAlgorithm.HS256)` que es más común en versiones recientes.

---

## Conclusiones (5 opiniones críticas)

1. **El laboratorio entrega una visión completa de JWT con Spring Security:** Desde el manejo de tokens hasta la autorización basada en roles, el proyecto cubre los aspectos esenciales de la autenticación moderna en aplicaciones Spring Boot.

2. **Los CommandLineRunner/DataInitializer son críticos:** El punto #1 (insertar roles al arrancar) demuestra que los scripts de inicialización son tan importantes como el código principal; un paso omitido puede hacer que toda la funcionalidad de sign-up fallen.

3. **La configuración de excepciones marca la diferencia:** Tener un `AuthenticationEntryPoint` y `AccessDeniedHandler` que respondan con JSON en lugar de errores HTML predeterminados mejora significativamente la experiencia del API consumer y el depuración.

4. **Las validaciones @Valid proporcionan retroalimentación temprana:** Las anotaciones `@NotBlank`, `@Email` y `@Size` en los DTOs, combinadas con `@Valid` en los controladores y un `@RestControllerAdvice`, atrapan errores del lado del cliente antes de que lleguen a la lógica de negocio.

5. **La versión 0.11.5 de jjwt requiere atención a imports:** Al usar jjwt 0.11.5, los imports de `Keys`, `SignatureAlgorithm` y el builder pattern difieren de las tutoriales más recientes. Es necesario revisar la documentación específica de la versión para evitar errores de compilación.

---

## Diagrama de flujo completo (Caso 6)

```
+------------------+     POST /api/auth/signin     +-------------------+
|   Postman        |  ──────────────────────────▶ |  AuthController   |
+------------------+                              +-------------------+
          │                                              │
          │  Credentials JSON: {username, password}      │
          │                                              │
          ▼                                              ▼
  +---------------------+                    +---------------------+
  |  AuthenticationManager    |  authenticate  |  UserDetailsService |
  +---------------------+                    +---------------------+
                                                   │
                                                   │ loadUserByUsername(username)
                                                   ▼
                                             +-----------------+
                                             |  UserDetailsImpl|
                                             +-----------------+
                                                   │
                                                   │ getPassword(), getAuthorities()
                                                   ▼
  +---------------------+    setAuthentication(auth)    +-------------------+
  |  SecurityContextHolder| ─────────────────────────────▶ | already set       |
  +---------------------+                            +-------------------+
                                                   │
                                                   │ generateJwtToken(auth)
                                                   ▼
  +---------------------+    setSubject(username)    +-------------------+
  |  JwtUtils (HS256)    | ──────────────────────────────▶ |  JWT Token        |
  +---------------------+    setExpiration(expiry)   +-------------------+
                                                   │
                                                   │ compact()
                                                   ▼
  +---------------------+    {"accessToken": "...",     │  Response JSON    |
  |  AuthController     | ──────────────────────────────▶ │  200 OK          |
  +---------------------+                            │  {token, type,   │
                                                   │   id, username,  │
                                                   │   email, roles}  │
                                                   +-------------------+

--- Para peticiones al API ---

Postman                              JwtAuthFilter                     SecurityContextHolder     Controller
  │                                      │                                    │
  │  GET /api/test/user                  │  extract token from Authorization   │
  │  header: Bearer <token>              │  header                              │
  │                                      ▼                                    │
  │                               JwtUtils.validateJwtToken()             │
  │                                      │  parseClaimsJws(token)              │
  │                                      │  getSubject(username)               │
  │                                      ▼                                    │
  │                           Check token validity   SecurityContextHolder    │
  │                                      │  setAuthentication(authenticated)   │
  │                                      │                                    │
  │                                      ▼                                    │
  │                             Authorized? ──────┘                    │
  │                                      │  Yes: 200 OK                      │
  │                                      │  No: 401 (AuthenticationEntryPoint)│
  │                                      │                                    │
  │                                      ▼                                    │
  │                            Has role? ───────────────────────┘    │
  │                                      │  Yes: 200 OK                      │
  │                                      │  No: 403 (AccessDeniedHandler)    │
  │                                      └───────────────────────────────────┘
```